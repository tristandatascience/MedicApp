package com.medicapp.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** Traitements d'image de la numérisation : rotation, redressement, contraste. */
object ScanPipeline {

    fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    /** Applique la rotation EXIF (degrés multiples de 90). */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Redressement de perspective : les 4 coins détectés/ajustés sont projetés
     * sur un rectangle. Les coins sont donnés dans l'ordre
     * haut-gauche, haut-droit, bas-droit, bas-gauche (coordonnées image).
     */
    fun perspectiveCrop(source: Bitmap, corners: FloatArray): Bitmap {
        require(corners.size == 8) { "4 coins attendus" }

        val left = minOf(corners[0], corners[6])
        val top = minOf(corners[1], corners[3])
        val right = maxOf(corners[2], corners[4])
        val bottom = maxOf(corners[5], corners[7])
        val width = (right - left).toInt().coerceAtLeast(1)
        val height = (bottom - top).toInt().coerceAtLeast(1)

        val dst = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(corners, 0, dst, 0, 4)) {
            matrix.setRectToRect(
                android.graphics.RectF(corners[0], corners[1], corners[2], corners[3]),
                android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Matrix.ScaleToFit.FILL,
            )
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.concat(matrix)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    /** Mode « scan » : niveaux de gris + contraste renforcé pour la lisibilité. */
    fun enhanceContrast(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        // Saturation 0 (gris) puis étirement de la luminance autour de 0.5.
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                1.35f, 0f, 0f, 0f, -45f,
                0f, 1.35f, 0f, 0f, -45f,
                0f, 0f, 1.35f, 0f, -45f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    /**
     * Heuristique de pré-positionnement des coins : le document papier occupe
     * presque toujours une zone centrale ; l'utilisateur ajuste ensuite.
     */
    fun defaultCorners(width: Int, height: Int): FloatArray {
        val insetX = width * 0.07f
        val insetY = height * 0.07f
        return floatArrayOf(
            insetX, insetY,                            // haut-gauche
            width - insetX, insetY,                    // haut-droit
            width - insetX, height - insetY,           // bas-droit
            insetX, height - insetY,                   // bas-gauche
        )
    }

    /** Génère un PDF A4 portrait à partir des pages (une image par page). */
    fun buildPdf(pages: List<Bitmap>): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        pages.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            // Ajustement « fit center » en préservant les proportions.
            val scale = minOf(
                pageWidth.toFloat() * 0.94f / bitmap.width,
                pageHeight.toFloat() * 0.94f / bitmap.height,
            )
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (pageWidth - drawWidth) / 2f
            val top = (pageHeight - drawHeight) / 2f
            canvas.drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            document.finishPage(page)
        }
        val baos = ByteArrayOutputStream()
        document.writeTo(baos)
        document.close()
        return baos.toByteArray()
    }
}
