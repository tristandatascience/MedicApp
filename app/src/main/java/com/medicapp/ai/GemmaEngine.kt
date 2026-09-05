package com.medicapp.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moteur IA embarqué optionnel : Gemma 3n E2B multimodal (LiteRT-LM).
 * Double emploi (§ évolutions) : transcription approfondie des documents
 * numérisés (tampons, écriture difficile) et base du futur assistant.
 *
 * Le modèle (~2 Go, quantifié int4) est téléchargé à la demande depuis
 * HuggingFace puis fonctionne intégralement hors ligne. Rien n'est envoyé
 * à un serveur.
 */
class GemmaEngine(private val context: Context) {

    @Volatile
    private var engine: Engine? = null

    /** Répertoire externe spécifique à l'application (accessible au DownloadManager). */
    fun modelFile(): File =
        File(File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), MODELS_DIR), MODEL_FILE_NAME)

    fun isInstalled(): Boolean = modelFile().length() > MIN_MODEL_BYTES

    fun installedSizeMb(): Long = if (isInstalled()) modelFile().length() / (1024 * 1024) else 0L

    /** Lance le téléchargement du modèle ; retourne l'identifiant DownloadManager. */
    fun startDownload(): Long {
        val request = android.app.DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Moteur IA — Dossier Médical")
            .setDescription("Gemma 3n E2B (≈ 2 Go)")
            .setDestinationInExternalFilesDir(
                context,
                android.os.Environment.DIRECTORY_DOWNLOADS,
                "$MODELS_DIR/$MODEL_FILE_NAME",
            )
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        // Certains CDN rejettent les requêtes sans User-Agent explicite.
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Android; DossierMedical)")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        return manager.enqueue(request)
    }

    /** Après ACTION_DOWNLOAD_COMPLETE : succès ou motif d'échec lisible. */
    fun downloadResult(downloadId: Long): Pair<Boolean, String> {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val cursor = manager.query(android.app.DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return false to "téléchargement introuvable"
            val status = it.getInt(it.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS))
            if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) return true to ""
            if (status == android.app.DownloadManager.STATUS_PAUSED) {
                return false to "en pause (réseau ou stockage en attente)"
            }
            val reason = it.getInt(it.getColumnIndex(android.app.DownloadManager.COLUMN_REASON))
            val message = when (reason) {
                android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                    "espace insuffisant — environ 2,5 Go libres requis"
                android.app.DownloadManager.ERROR_FILE_ERROR -> "erreur d'écriture du fichier"
                android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE, android.app.DownloadManager.ERROR_HTTP_DATA_ERROR ->
                    "erreur réseau/HTTP pendant le transfert (code $reason) — réessayez, ou importez le fichier manuellement"
                android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "trop de redirections vers le serveur du modèle"
                android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "stockage externe indisponible"
                android.app.DownloadManager.ERROR_CANNOT_RESUME -> "téléchargement interrompu, impossible de reprendre"
                else -> "échec (raison $reason)"
            }
            return false to message
        }
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
        activeEngine.createConversation().use { conversation ->
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
        val config = EngineConfig(
            modelPath = modelFile().absolutePath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
        )
        val created = Engine(config)
        created.initialize()
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
        const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"
        const val MODEL_LABEL = "Gemma 3n E2B (int4, ≈ 2 Go)"

        /** En dessous, le fichier est considéré incomplet. */
        private const val MIN_MODEL_BYTES = 500_000_000L

        private val TRANSCRIBE_PROMPT = """
            Tu es un assistant de transcription de documents médicaux personnels.
            Transcris fidèlement et intégralement tout le texte visible de cette
            page, y compris les tampons, les en-têtes, les mentions manuscrites
            lisibles et les annotations en marge. Conserve l'ordre de lecture
            naturel (de haut en bas). Si une partie est illisible, écris
            [illisible]. Réponds uniquement avec la transcription, sans
            commentaire ni interprétation médicale.
        """.trimIndent()
    }
}
