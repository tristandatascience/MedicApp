package com.medicapp.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Étape 2 de la numérisation : ajustement des 4 coins du document et
 * amélioration du contraste (§ 4.7). Les coins sont pré-positionnés par
 * heuristique puis ajustés au doigt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    jpegBytes: ByteArray,
    onValidate: (storedJpeg: ByteArray, ocrJpeg: ByteArray) -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    // Résolution plafonnée : suffisante pour l'OCR et le PDF, évite les OOM.
    val decoded = remember(jpegBytes) { ScanPipeline.decodeCapped(jpegBytes, 2600) }
    if (decoded == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Image illisible.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCancel) { Text("Reprendre la photo") }
        }
        return
    }
    val bitmap = decoded
    val imageBitmap: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val scope = rememberCoroutineScope()

    var corners by remember(bitmap) {
        mutableStateOf(ScanPipeline.defaultCorners(bitmap.width, bitmap.height))
    }
    var enhance by remember { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Ajustement « fit center » de l'image dans la zone de recadrage.
    val scale = if (canvasSize.width > 0 && canvasSize.height > 0) {
        minOf(
            canvasSize.width.toFloat() / bitmap.width,
            canvasSize.height.toFloat() / bitmap.height,
        )
    } else 1f
    val displayWidth = bitmap.width * scale
    val displayHeight = bitmap.height * scale
    val offsetX = (canvasSize.width - displayWidth) / 2f
    val offsetY = (canvasSize.height - displayHeight) / 2f

    fun toScreen(pointIndex: Int): Offset =
        Offset(offsetX + corners[pointIndex * 2] * scale, offsetY + corners[pointIndex * 2 + 1] * scale)

    fun toImage(screen: Offset, pointIndex: Int): Pair<Float, Float> = Pair(
        ((screen.x - offsetX) / scale).coerceIn(0f, bitmap.width.toFloat()),
        ((screen.y - offsetY) / scale).coerceIn(0f, bitmap.height.toFloat()),
    )

    var draggedCorner by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recadrer la page") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Reprendre la photo")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { start ->
                                draggedCorner = (0 until 4).minByOrNull { index ->
                                    val p = toScreen(index)
                                    sqrt(
                                        (p.x - start.x) * (p.x - start.x) + (p.y - start.y) * (p.y - start.y)
                                    )
                                }?.takeIf { index ->
                                    val p = toScreen(index)
                                    abs(p.x - start.x) < 120f && abs(p.y - start.y) < 120f
                                } ?: -1
                            },
                            onDrag = { change, _ ->
                                if (draggedCorner >= 0) {
                                    val (x, y) = toImage(change.position, draggedCorner)
                                    corners = corners.copyOf().also {
                                        it[draggedCorner * 2] = x
                                        it[draggedCorner * 2 + 1] = y
                                    }
                                }
                            },
                            onDragEnd = { draggedCorner = -1 },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(displayWidth.toInt(), displayHeight.toInt()),
                    )
                    val path = Path().apply {
                        moveTo(toScreen(0).x, toScreen(0).y)
                        lineTo(toScreen(1).x, toScreen(1).y)
                        lineTo(toScreen(2).x, toScreen(2).y)
                        lineTo(toScreen(3).x, toScreen(3).y)
                        close()
                    }
                    drawPath(path, Color(0xFF33CCAA).copy(alpha = 0.25f))
                    drawPath(path, Color(0xFF22BBAA), style = Stroke(width = 3f))
                    repeat(4) { index ->
                        drawCircle(Color.White, radius = 22f, center = toScreen(index))
                        drawCircle(Color(0xFF006A60), radius = 16f, center = toScreen(index))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Améliorer le contraste", Modifier.weight(1f))
                Switch(checked = enhance, onCheckedChange = { enhance = it })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val currentCorners = corners
                        scope.launch(Dispatchers.Default) {
                            val cropped = ScanPipeline.perspectiveCrop(bitmap, currentCorners)
                            // Version stockée (contraste renforcé si demandé) et
                            // version brute conservée pour l'OCR.
                            val stored = if (enhance) ScanPipeline.enhanceContrast(cropped) else cropped
                            val storedJpeg = ScanPipeline.toJpeg(stored, 90)
                            val ocrJpeg = if (stored === cropped) storedJpeg else ScanPipeline.toJpeg(cropped, 92)
                            withContext(Dispatchers.Main) { onValidate(storedJpeg, ocrJpeg) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Valider la page") }
                TextButton(onClick = onSkip) { Text("Garder telle quelle") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
