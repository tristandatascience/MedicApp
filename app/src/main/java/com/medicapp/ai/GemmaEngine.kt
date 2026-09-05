package com.medicapp.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Moteur IA embarqué optionnel : Gemma 4 E2B multimodal (LiteRT-LM), hébergé
 * par l'organisation communautaire officielle LiteRT (accès libre, sans
 * compte). Double emploi (§ évolutions) : transcription approfondie des
 * documents numérisés (tampons, écriture difficile) et base du futur
 * assistant.
 *
 * Le modèle (~2,6 Go) est téléchargé à la demande puis fonctionne
 * intégralement hors ligne. Rien n'est envoyé à un serveur.
 */
class GemmaEngine(private val context: Context) {

    @Volatile
    private var engine: Engine? = null

    /** Répertoire externe spécifique à l'application (accessible au DownloadManager). */
    fun modelFile(): File =
        File(File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), MODELS_DIR), MODEL_FILE_NAME)

    fun isInstalled(): Boolean = modelFile().length() > MIN_MODEL_BYTES

    fun installedSizeMb(): Long = if (isInstalled()) modelFile().length() / (1024 * 1024) else 0L

    // ------------------------------------------------------------------
    // Téléchargement intégré (sans dépendre du navigateur ni du
    // DownloadManager) : progression observable et reprise sur interruption.
    // ------------------------------------------------------------------

    /** null = pas de téléchargement en cours ; sinon pourcentage 0..100. */
    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError

    @Volatile
    private var cancelRequested = false

    fun cancelDownload() {
        cancelRequested = true
    }

    fun dismissDownloadError() {
        _downloadError.value = null
    }

    /**
     * Télécharge le modèle (~2,6 Go) directement dans l'application.
     * Reprend automatiquement un téléchargement partiel (en-tête Range).
     */
    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        cancelRequested = false
        _downloadProgress.value = 0
        try {
            val target = modelFile()
            target.parentFile?.mkdirs()
            val complete = performDownload(MODEL_URL, target)
            _downloadProgress.value = 100
            complete
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _downloadError.value = "Échec du téléchargement : " +
                (e.message ?: e.javaClass.simpleName) +
                " — la reprise sera proposée au prochain essai."
            false
        } finally {
            _downloadProgress.value = null
        }
    }

    private fun performDownload(url: String, target: File): Boolean {
        var resumeFrom = if (target.exists()) target.length() else 0L
        var connection = openConnection(url, resumeFrom)

        // Reprise refusée (416 = déjà complet ou range non géré) : on repart de zéro.
        if (connection.responseCode == 416) {
            if (isInstalled()) return true
            resumeFrom = 0L
            connection.disconnect()
            connection = openConnection(url, 0L)
        }

        val code = connection.responseCode
        if (code != 200 && code != 206) {
            connection.disconnect()
            throw IllegalStateException("le serveur a répondu HTTP $code")
        }

        val appending = code == 206 && resumeFrom > 0
        if (!appending) resumeFrom = 0L
        val declaredTotal = connection.contentLengthLong
        val total = if (declaredTotal > 0) declaredTotal + resumeFrom else -1L

        connection.inputStream.buffered().use { input ->
            java.io.FileOutputStream(target, appending).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var lastPercent = -1
                while (input.read(buffer).also { read = it } >= 0) {
                    if (cancelRequested) {
                        output.flush()
                        return false
                    }
                    output.write(buffer, 0, read)
                    resumeFrom += read
                    if (total > 0) {
                        val percent = ((resumeFrom * 100) / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _downloadProgress.value = percent.coerceIn(0, 100)
                        }
                    }
                }
                output.flush()
            }
        }
        connection.disconnect()
        return target.length() > MIN_MODEL_BYTES
    }

    private fun openConnection(url: String, resumeFrom: Long): java.net.HttpURLConnection {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; DossierMedical)")
        if (resumeFrom > 0) {
            connection.setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        return connection
    }

    /** Copie un modèle déjà téléchargé (navigateur…) vers l'emplacement de l'application. */
    suspend fun importModel(source: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = modelFile()
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null && isInstalled()
        }.getOrDefault(false)
    }

    /**
     * Transcrit une page (JPEG) avec le VLM : tampons, mises en page complexes
     * et écriture difficile mieux traités que par l'OCR classique.
     */
    suspend fun transcribe(jpegBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val activeEngine = ensureEngine()
        val config = ConversationConfig(
            maxOutputToken = 2048,
            systemInstruction = Contents.of(
                Content.Text(
                    "Tu es un transcriveur méticuleux de documents médicaux personnels. " +
                        "Tu décris uniquement ce qui est visible, sans jamais interpréter, " +
                        "diagnostiquer ni conseiller. Tu respectes strictement le format " +
                        "de sortie demandé."
                )
            ),
        )
        activeEngine.createConversation(config).use { conversation ->
            val response = conversation.sendMessage(
                Contents.of(
                    Content.ImageBytes(jpegBytes),
                    Content.Text(TRANSCRIBE_PROMPT),
                )
            )
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        }
    }

    @Synchronized
    private fun ensureEngine(): Engine {
        engine?.let { return it }
        check(isInstalled()) {
            "Modèle IA non téléchargé. Réglages → Intelligence artificielle."
        }
        val modelPath = modelFile().absolutePath
        // GPU (OpenCL) nettement plus rapide quand disponible ; repli CPU sinon.
        val created = runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    maxNumTokens = 4096,
                    maxNumImages = 1,
                )
            ).also { it.initialize() }
        }.getOrElse {
            val cpu = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    maxNumTokens = 4096,
                    maxNumImages = 1,
                )
            )
            cpu.initialize()
            cpu
        }
        engine = created
        return created
    }

    /** Libère la mémoire du moteur (appelé après une série de transcriptions). */
    @Synchronized
    fun releaseEngine() {
        runCatching { engine?.close() }
        engine = null
    }

    fun deleteModel() {
        releaseEngine()
        modelFile().delete()
    }

    companion object {
        private const val MODELS_DIR = "models"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"
        const val MODEL_LABEL = "Gemma 4 E2B (≈ 2,6 Go)"

        /** En dessous, le fichier est considéré incomplet. */
        private const val MIN_MODEL_BYTES = 500_000_000L

        private val TRANSCRIBE_PROMPT = """
            Examine attentivement cette page de document médical, y compris les
            zones peu contrastées, les tampons et les marges. Transcris-la en
            respectant STRICTEMENT ce format, une section par ligne, dans cet
            ordre. Si une information est absente ou illisible, écris
            "absent" ou "[illisible]" — n'invente jamais rien.

            TYPE : (ordonnance de médicaments / ordonnance de biologie / ordonnance kiné ou paramédicale / lettre d'orientation / résultat d'examen / carnet de vaccination / autre)
            EN-TÊTE : (noms et coordonnées des médecins, cabinets ou laboratoires imprimés en haut de page)
            TAMPON : (texte exact du ou des tampons, même écrit en rond ou incliné)
            DATE : (chaque date visible, au format JJ/MM/AAAA)
            PATIENT : (nom du patient si visible)
            CORPS : (transcription intégrale ligne par ligne du corps du document : médicaments avec dosages, analyses prescrites, actes, quantités et durées)
            MANUSCRIT : (toute mention écrite à la main et lisible)
            PIED DE PAGE : (mentions du bas de page, signature, paraphe, docteur je soussigné, etc.)

            Réponds uniquement avec ces sections, sans commentaire.
        """.trimIndent()
    }
}
