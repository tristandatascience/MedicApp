package com.medicapp.ui.modules.exams

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.Exam
import com.medicapp.data.db.entity.ExamCategory
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.DateField
import com.medicapp.ui.common.DetailRow
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import com.medicapp.data.repo.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ExamsViewModel(private val container: AppContainer) : ViewModel() {

    val list = container.settings.settings
        .flatMapLatest { container.examRepository.observeForProfile(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Exam? = container.examRepository.getById(id)

    suspend fun getDocument(id: Long) = container.documentRepository.getById(id)

    suspend fun currentProfileId(): Long = container.settings.current().currentProfileId

    fun save(exam: Exam, attachDocumentId: Long? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = container.examRepository.upsert(exam)
            attachDocumentId?.let {
                container.documentRepository.reassign(
                    it,
                    com.medicapp.data.db.entity.DocumentOwner.EXAM,
                    id,
                )
            }
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.examRepository.delete(id) }
    }
}

fun examCategoryLabel(category: ExamCategory): String = when (category) {
    ExamCategory.BLOOD_TEST -> "Analyse sanguine"
    ExamCategory.RADIOLOGY -> "Radiologie"
    ExamCategory.ULTRASOUND -> "Échographie"
    ExamCategory.MRI -> "IRM"
    ExamCategory.OTHER -> "Autre"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenForm: (Long) -> Unit = {},
    onScan: () -> Unit = {},
) {
    val vm: ExamsViewModel = containerViewModel { ExamsViewModel(it) }
    val list by vm.list.collectAsState()
    var categoryFilter by remember { mutableStateOf<ExamCategory?>(null) }

    val filtered = if (categoryFilter == null) list else list.filter { it.category == categoryFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Résultats d'examens") },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(
                            Icons.Outlined.DocumentScanner,
                            contentDescription = "Numériser un résultat d'examen",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(-1L) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter un examen")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("Tous") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                ExamCategory.entries.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category,
                        onClick = { categoryFilter = category },
                        label = { Text(examCategoryLabel(category)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    "Aucun résultat d'examen.\nNumérisez vos analyses : le texte est indexé pour la recherche " +
                        "(ex. retrouver « glycémie »). Aucune valeur n'est interprétée.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { exam ->
                        Card(
                            onClick = { onOpenDetail(exam.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    exam.title ?: examCategoryLabel(exam.category),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${examCategoryLabel(exam.category)} — ${Format.dateShort(exam.examDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                exam.laboratory?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: ExamsViewModel = containerViewModel { ExamsViewModel(it) }
    var exam by remember { mutableStateOf<Exam?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { exam = vm.get(id) }
    val e = exam

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e?.title ?: "Examen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(id) }) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer") }
                },
            )
        },
    ) { padding ->
        if (e == null) {
            Column(Modifier.fillMaxSize().padding(padding)) { Text("Examen introuvable.") }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            DetailRow("Type d'examen", examCategoryLabel(e.category))
            DetailRow("Date", Format.date(e.examDate))
            e.laboratory?.let { DetailRow("Laboratoire / établissement", it) }
            e.prescriber?.let { DetailRow("Médecin prescripteur", it) }
            e.notes?.let { DetailRow("Notes", it) }
            Spacer(Modifier.height(16.dp))
            com.medicapp.ui.documents.DocumentsSection(
                owner = com.medicapp.data.db.entity.DocumentOwner.EXAM,
                ownerId = id,
                onScan = onScan,
                onOpenDocument = onOpenDocument,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cet examen ?") },
            text = { Text("La fiche et ses documents joints seront supprimés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id); confirmDelete = false; onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamFormScreen(id: Long, scanDocumentId: Long? = null, onBack: () -> Unit) {
    val vm: ExamsViewModel = containerViewModel { ExamsViewModel(it) }

    var existing by remember { mutableStateOf<Exam?>(null) }
    var profileId by remember { mutableStateOf(0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExamCategory.BLOOD_TEST) }
    var examDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var laboratory by rememberSaveable { mutableStateOf("") }
    var prescriber by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var prefilledFromScan by remember { mutableStateOf(false) }

    // Pré-remplissage depuis le résultat numérisé (validation manuelle, § 4.5 :
    // l'OCR sert à l'indexation, aucune valeur n'est interprétée).
    LaunchedEffect(scanDocumentId) {
        if (scanDocumentId != null && id <= 0) {
            val doc = vm.getDocument(scanDocumentId)
            val text = doc?.ocrText
            if (!text.isNullOrBlank()) {
                val parsed = com.medicapp.ocr.OcrFieldParser.parse(text)
                parsed.examCategory?.let { category = it }
                parsed.mostLikelyDate?.let { if (examDate == null) examDate = it }
                parsed.laboratory?.let { if (laboratory.isBlank()) laboratory = it }
                parsed.prescriber?.let { if (prescriber.isBlank()) prescriber = it }
                prefilledFromScan = true
            } else if (doc != null) {
                // OCR sans texte exploitable : saisie manuelle, document joint à l'enregistrement.
                prefilledFromScan = true
            }
        }
    }

    LaunchedEffect(id) {
        if (id > 0) existing = vm.get(id)
        profileId = existing?.profileId ?: vm.currentProfileId()
    }
    LaunchedEffect(existing) {
        val e = existing
        if (!initialized && e != null) {
            title = e.title ?: ""
            category = e.category
            examDate = e.examDate
            laboratory = e.laboratory ?: ""
            prescriber = e.prescriber ?: ""
            notes = e.notes ?: ""
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "Modifier l'examen" else "Nouvel examen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (prefilledFromScan) {
                com.medicapp.ui.common.ScanPrefillBanner()
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Intitulé (ex. Bilan sanguin annuel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ExamCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(examCategoryLabel(c)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            DateField(label = "Date de l'examen *", value = examDate, onChange = { examDate = it })
            OutlinedTextField(
                value = laboratory,
                onValueChange = { laboratory = it },
                label = { Text("Laboratoire / établissement") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prescriber,
                onValueChange = { prescriber = it },
                label = { Text("Médecin prescripteur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val e = existing
                    vm.save(
                        Exam(
                            id = e?.id ?: 0L,
                            profileId = e?.profileId ?: profileId,
                            title = title.trim().ifBlank { null },
                            category = category,
                            examDate = examDate ?: LocalDate.now(),
                            laboratory = laboratory.trim().ifBlank { null },
                            prescriber = prescriber.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                            createdAt = e?.createdAt ?: LocalDateTime.now(),
                        ),
                        attachDocumentId = scanDocumentId,
                        onDone = onBack,
                    )
                },
                enabled = examDate != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}
