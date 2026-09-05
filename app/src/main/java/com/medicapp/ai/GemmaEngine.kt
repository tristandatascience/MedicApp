package com.medicapp.ai

import android.content.Context
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
        File(File(context.getExternalFilesDir(null), MODELS_DIR), MODEL_FILE_NAME)

    fun isInstalled(): Boolean = modelFile().length() > MIN_MODEL_BYTES

    fun installedSizeMb(): Long = if (isInstalled()) modelFile().length() / (1024 * 1024) else 0L

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
