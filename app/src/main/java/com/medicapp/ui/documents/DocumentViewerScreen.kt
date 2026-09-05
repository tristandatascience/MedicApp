package com.medicapp.ui.documents

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.DocumentEntity
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentViewerViewModel(private val container: AppContainer) : ViewModel() {

    /** Moteur IA optionnel (Gemma 3n) : installé et utilisable ? */
    val aiInstalled: Boolean get() = container.gemmaEngine.isInstalled()

    fun observe(id: Long) = container.documentRepository.observeById(id)

    /** Déchiffre et met en pages le document (rendu PDF ou image). */
    suspend fun renderPages(
        storageKey: String,
        mimeType: String,
        maxWidth: Int = 1080,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bytes = container.documentRepository.open(storageKey)
        if (mimeType == "application/pdf") {
            val temp = File.createTempFile("medic-view", ".pdf")
            try {
                temp.writeBytes(bytes)
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        (0 until renderer.pageCount).mapNotNull { index ->
                            renderer.openPage(index).use { page ->
                                val scale = maxWidth.toFloat() / page.width
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1),
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        }
                    }
                }
            } finally {
                temp.delete()
            }
        } else {
            listOfNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }

    fun saveOcr(id: Long, text: String) {
        viewModelScope.launch { container.documentRepository.updateOcr(id, text.trim().ifBlank { null }) }
    }

    fun aiEnhance(
        storageKey: String,
        mimeType: String,
        onDone: (text: String?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            val outcome = runCatching {
                // Haute résolution pour le VLM : 2048 px (le rendu écran 1080 px
                // est trop pauvre pour les petits textes et les tampons).
                val bitmaps = renderPages(storageKey, mimeType, maxWidth = 2048)
                check(bitmaps.isNotEmpty()) { "document illisible" }
                val sb = StringBuilder()
                bitmaps.forEachIndexed { index, bitmap ->
                    val jpeg = com.medicapp.scan.ScanPipeline.toJpeg(bitmap, 92)
                    val pageText = container.gemmaEngine.transcribe(jpeg).trim()
                    if (bitmaps.size > 1) sb.append("=== Page ${index + 1} ===\n")
                    sb.append(pageText)
                    if (index < bitmaps.size - 1) sb.append("\n\n")
                }
                container.gemmaEngine.releaseEngine()
                sb.toString().trim()
            }
            withContext(Dispatchers.Main) {
                val text = outcome.getOrNull()?.takeIf { it.isNotBlank() }
                val error = when {
                    text == null -> outcome.exceptionOrNull()?.message ?: "aucun texte reconnu"
                    text.length < 40 ->
                        "le modèle n'a presque rien reconnu (réponse trop courte) — " +
                            "essayez de reprendre la photo de plus près, à plat et bien éclairée"
                    else -> null
                }
                onDone(if (error == null) text else null, error)
            }
        }
    }

    fun saveTitle(id: Long, title: String) {
        viewModelScope.launch { container.documentRepository.updateTitle(id, title) }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            container.documentRepository.delete(id)
            onDone()
        }
    }
}

/** Consultation d'un document numérisé : aperçu et texte OCR corrigeable (§ 4.7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    id: Long,
    onBack: () -> Unit,
) {
    val vm: DocumentViewerViewModel = containerViewModel { DocumentViewerViewModel(it) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val document by remember(id) { vm.observe(id) }.collectAsState(initial = null)
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var ocrText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loadedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(document) {
        val doc = document ?: return@LaunchedEffect
        if (loadedKey != doc.storageKey) {
            loadedKey = doc.storageKey
            pages = vm.renderPages(doc.storageKey, doc.mimeType)
        }
        if (ocrText.isEmpty()) ocrText = doc.ocrText ?: ""
    }

    val doc = document

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.title ?: "Document", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, ocrText)
                        context.startActivity(Intent.createChooser(intent, "Copier vers…"))
                    }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Partager le texte") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Supprimer")
                    }
                },
            )
        },
    ) { padding ->
        if (doc == null) {
            Column(Modifier.fillMaxSize().padding(padding)) { Text("Document introuvable.") }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Document") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Texte reconnu") })
            }
            if (tab == 0) {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(pages.size) { index ->
                        Image(
                            bitmap = pages[index].asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                    item {
                        Text(
                            "Document chiffré — ${doc.pageCount} page(s) — ajouté le ${Format.date(doc.createdAt.toLocalDate())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        "Texte reconnu par OCR — vous pouvez le corriger. Il sert uniquement " +
                            "à la recherche dans le dossier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Transcription approfondie par le moteur IA embarqué (bêta).
                    var aiBusy by remember { mutableStateOf(false) }
                    if (vm.aiInstalled && !aiBusy) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                aiBusy = true
                                vm.aiEnhance(doc.storageKey, doc.mimeType) { text, error ->
                                    aiBusy = false
                                    if (text != null) {
                                        ocrText = text
                                        Toast.makeText(
                                            context,
                                            "Transcription IA effectuée — vérifiez puis enregistrez",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Analyse IA impossible : $error",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Améliorer la transcription avec l'IA (bêta)")
                        }
                    }
                    if (aiBusy) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.padding(end = 12.dp).height(24.dp)
                            )
                            Text(
                                "Analyse IA en cours — jusqu'à une minute par page…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = ocrText,
                        onValueChange = { ocrText = it },
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        label = { Text("Texte OCR") },
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { vm.saveOcr(doc.id, ocrText) }) {
                            Text("Enregistrer le texte", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(ocrText)) }) {
                            Text("Copier")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce document ?") },
            text = { Text("Le fichier chiffré sera définitivement effacé.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id) { onBack() }
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
