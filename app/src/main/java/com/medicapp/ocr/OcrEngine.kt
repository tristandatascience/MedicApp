package com.medicapp.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OCR embarqué ML Kit (modèle latin, téléchargé avec l'application :
 * fonctionne hors ligne). Le texte extrait sert uniquement à l'indexation
 * et à la recherche — aucune interprétation des valeurs (§ 4.5).
 */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { continuation.resume("") } // l'OCR ne bloque jamais l'enregistrement
    }

    fun close() = recognizer.close()
}
