package com.medicapp.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.ui.common.containerViewModel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private suspend fun <T> ListenableFuture<T>.await(executor: java.util.concurrent.Executor): T =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.cancel(e)
            }
        }, executor)
    }

/**
 * Flux de numérisation en 3 étapes maximum : photo -> recadrage -> validation
 * avec OCR automatique (§ 8 du cahier des charges). Multi-pages : chaque page
 * enchaîne photo puis recadrage avant la page suivante.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanFlowScreen(
    owner: DocumentOwner,
    ownerId: Long?,
    onDone: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: ScanViewModel = containerViewModel { ScanViewModel(it) }
    val pages by vm.pages.collectAsState()
    val processing by vm.processing.collectAsState()
    val error by vm.error.collectAsState()

    var cropInput by remember { mutableStateOf<ByteArray?>(null) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }

    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text("Numérisation interrompue") },
            text = { Text(error ?: "") },
            confirmButton = {
                TextButton(onClick = vm::dismissError) { Text("Réessayer") }
            },
        )
    }

    when {
        processing -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("OCR et enregistrement chiffré…")
                }
            }
        }

        cropInput != null -> {
            CropScreen(
                jpegBytes = cropInput!!,
                onValidate = { storedJpeg, ocrJpeg ->
                    vm.addPage(storedJpeg, ocrJpeg)
                    cropInput = null
                },
                onSkip = {
                    vm.addPage(cropInput!!)
                    cropInput = null
                },
                onCancel = { cropInput = null },
            )
        }

        else -> {
            CameraScreen(
                pageCount = pages.size,
                onPageCaptured = { cropInput = it },
                onImportImage = { cropInput = it },
                onImportPdf = { bytes ->
                    vm.finalizeImportedPdf(owner, ownerId, bytes) { documentId -> onDone(documentId) }
                },
                onFinish = { showTitleDialog = true },
                onBack = onCancel,
            )
        }
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Enregistrer le document") },
            text = {
                Column {
                    Text("${pages.size} page(s) numérisée(s).")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titre du document") },
                        singleLine = true,
                    )
                    Text(
                        "Le texte sera reconnu automatiquement (OCR hors ligne) pour la recherche.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTitleDialog = false
                    vm.finalize(owner, ownerId, title) { documentId -> onDone(documentId) }
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Continuer le scan") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraScreen(
    pageCount: Int,
    onPageCaptured: (ByteArray) -> Unit,
    onImportImage: (ByteArray) -> Unit,
    onImportPdf: (ByteArray) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            // Qualité maximale du capteur : indispensable pour les petits textes
            // des ordonnances (le mode latence plafonne vers 1080p).
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { bytes ->
                    withContext(Dispatchers.Main) {
                        if (bytes.size > 4 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-") {
                            onImportPdf(bytes)
                        } else if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                            onImportImage(bytes)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Liaison unique caméra <-> cycle de vie (prévisualisation + capture).
    LaunchedEffect(previewView, hasPermission) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        try {
            val provider = ProcessCameraProvider.getInstance(context)
                .await(ContextCompat.getMainExecutor(context))
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Annulation normale (navigation hors de l'écran) : pas une erreur.
            throw e
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Caméra indisponible : ${e.message ?: "erreur inconnue"}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun capture() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    scope.launch(Dispatchers.Default) {
                        val normalized = normalizeJpeg(bytes, rotation)
                        withContext(Dispatchers.Main) { onPageCaptured(normalized) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    android.widget.Toast.makeText(
                        context,
                        "Échec de la prise de photo : ${exception.message ?: "erreur caméra"}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pageCount > 0) "Numérisation — ${pageCount} page(s)" else "Numérisation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annuler")
                    }
                },
            )
        },
    ) { padding ->
        if (!hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("L'accès à l'appareil photo est nécessaire pour numériser vos documents.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Autoriser l'appareil photo")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                    Text("Importer un fichier")
                }
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (pageCount > 0) {
                    Text(
                        "$pageCount page(s) capturée(s) — « Terminer » pour enregistrer",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { importLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = "Importer depuis la galerie ou des fichiers",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { capture() },
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Camera,
                            contentDescription = "Photographier la page",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                if (pageCount > 0) {
                    Button(onClick = onFinish) { Text("Terminer") }
                } else {
                    Spacer(Modifier.size(64.dp))
                }
            }
        }
    }
}

/** Décode (résolution plafonnée), redresse selon la rotation CameraX et ré-encode. */
private fun normalizeJpeg(bytes: ByteArray, rotationDegrees: Int): ByteArray {
    val bitmap = ScanPipeline.decodeCapped(bytes, 3200) ?: return bytes
    val rotated = if (rotationDegrees != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else bitmap
    if (rotated != bitmap) bitmap.recycle()
    val output = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 95, output)
    rotated.recycle()
    return output.toByteArray()
}
