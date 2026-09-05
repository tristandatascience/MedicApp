package com.medicapp.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.di.AppContainer
import com.medicapp.ocr.OcrEngine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parcours de numérisation (§ 4.7) : capture de pages, recadrage,
 * puis enregistrement du document chiffré avec OCR automatique.
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {

    data class ScanPage(
        val id: String = UUID.randomUUID().toString(),
        /** Page telle que stockée (recadrée, éventuellement contraste renforcé). */
        val jpeg: ByteArray,
        /** Version sans traitement pour l'OCR (le renforcement de contraste
         *  peut dégrader les textes fins des ordonnances). */
        val ocrJpeg: ByteArray = jpeg,
    )

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val ocrEngine = OcrEngine()

    fun dismissError() {
        _error.value = null
    }

    fun addPage(jpeg: ByteArray, ocrJpeg: ByteArray = jpeg) {
        _pages.value = _pages.value + ScanPage(jpeg = jpeg, ocrJpeg = ocrJpeg)
    }

    fun removePage(pageId: String) {
        _pages.value = _pages.value.filterNot { it.id == pageId }
    }

    fun pageCount(): Int = _pages.value.size

    /**
     * Assemble le PDF, exécute l'OCR de chaque page puis enregistre le
     * document chiffré. Retourne l'identifiant du document créé.
     * Les pages capturées sont conservées en cas d'échec (nouvel essai possible).
     */
    fun finalize(
        owner: DocumentOwner,
        ownerId: Long?,
        title: String,
        onDone: (Long) -> Unit,
    ) {
        if (_pages.value.isEmpty()) return
        _processing.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val profileId = container.settings.current().currentProfileId
                val documentId = withContext(Dispatchers.IO) {
                    val bitmaps = _pages.value.mapNotNull { ScanPipeline.decodeCapped(it.jpeg, 2600) }
                    check(bitmaps.isNotEmpty()) {
                        "Aucune page exploitable n'a été capturée. Reprenez la photo."
                    }
                    val pdfBytes = ScanPipeline.buildPdf(bitmaps)

                    // OCR page par page sur la version SANS traitement (modèle
                    // embarqué, hors ligne) ; si le résultat est pauvre, seconde
                    // tentative sur la version stockée et on garde la meilleure.
                    val ocrText = StringBuilder()
                    _pages.value.forEachIndexed { index, page ->
                        val plain = ScanPipeline.decodeCapped(page.ocrJpeg, 2600)
                        var text = plain?.let { ocrEngine.recognize(it).trim() } ?: ""
                        if (text.length < MIN_GOOD_OCR_CHARS && plain != bitmaps.getOrNull(index)) {
                            val alt = ocrEngine.recognize(bitmaps[index]).trim()
                            if (alt.length > text.length) text = alt
                        }
                        if (_pages.value.size > 1) ocrText.append("=== Page ${index + 1} ===\n")
                        ocrText.append(text)
                        if (index < _pages.value.size - 1) ocrText.append("\n\n")
                    }

                    container.documentRepository.create(
                        profileId = profileId,
                        title = title.ifBlank { "Document du ${java.time.LocalDate.now()}" },
                        mimeType = "application/pdf",
                        pageCount = bitmaps.size,
                        bytes = pdfBytes,
                        owner = owner,
                        ownerId = ownerId,
                        ocrText = ocrText.toString().trim().ifBlank { null },
                    )
                }
                _processing.value = false
                _pages.value = emptyList()
                onDone(documentId)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Échec de l'enregistrement du scan", e)
                _processing.value = false
                _error.value = "Échec de l'enregistrement du document : " +
                    (e.message ?: "erreur inconnue") +
                    ". Les pages capturées sont conservées, vous pouvez réessayer."
            }
        }
    }

    /**
     * Import direct d'un PDF existant : enregistré chiffré sans OCR
     * (l'OCR automatique ne s'applique qu'aux captures).
     */
    fun finalizeImportedPdf(owner: DocumentOwner, ownerId: Long?, bytes: ByteArray, onDone: (Long) -> Unit) {
        _processing.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val profileId = container.settings.current().currentProfileId
                val documentId = withContext(Dispatchers.IO) {
                    container.documentRepository.create(
                        profileId = profileId,
                        title = "Document importé du ${java.time.LocalDate.now()}",
                        mimeType = "application/pdf",
                        pageCount = countPdfPages(bytes),
                        bytes = bytes,
                        owner = owner,
                        ownerId = ownerId,
                        ocrText = null,
                    )
                }
                _processing.value = false
                onDone(documentId)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Échec de l'import du PDF", e)
                _processing.value = false
                _error.value = "Échec de l'import du PDF : " +
                    (e.message ?: "erreur inconnue")
            }
        }
    }

    private fun countPdfPages(bytes: ByteArray): Int {
        val temp = java.io.File.createTempFile("medic-import", ".pdf")
        return try {
            temp.writeBytes(bytes)
            android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) {
            1
        } finally {
            temp.delete()
        }
    }

    override fun onCleared() {
        ocrEngine.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ScanViewModel"

        /** En dessous, l'OCR est considéré pauvre : on tente la variante stockée. */
        private const val MIN_GOOD_OCR_CHARS = 120
    }
}
