package com.medicapp.ui.modules.treatments

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.Treatment
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.DateField
import com.medicapp.ui.common.DetailRow
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.FormSectionTitle
import com.medicapp.ui.common.TimeField
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TreatmentsViewModel(private val container: AppContainer) : ViewModel() {

    val active = container.settings.settings
        .flatMapLatest { container.treatmentRepository.observeActive(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history = container.settings.settings
        .flatMapLatest { container.treatmentRepository.observeHistory(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prescriptions = container.settings.settings
        .flatMapLatest { container.prescriptionRepository.observeForProfile(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Treatment? = container.treatmentRepository.getById(id)

    suspend fun getDocument(id: Long) = container.documentRepository.getById(id)

    suspend fun currentProfileId(): Long = container.settings.current().currentProfileId

    fun save(treatment: Treatment, attachDocumentId: Long? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = container.treatmentRepository.upsert(treatment)
            attachDocumentId?.let {
                container.documentRepository.reassign(
                    it,
                    com.medicapp.data.db.entity.DocumentOwner.TREATMENT,
                    id,
                )
            }
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.treatmentRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentsScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenForm: (Long) -> Unit = {},
    onScan: () -> Unit = {},
) {
    val vm: TreatmentsViewModel = containerViewModel { TreatmentsViewModel(it) }
    val active by vm.active.collectAsState()
    val history by vm.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    val list = if (showHistory) history else active

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traitements") },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(
                            Icons.Outlined.DocumentScanner,
                            contentDescription = "Numériser une ordonnance",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(-1L) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter un traitement")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showHistory, onClick = { showHistory = false }, label = { Text("En cours") })
                FilterChip(selected = showHistory, onClick = { showHistory = true }, label = { Text("Historique") })
            }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Text(
                    if (showHistory) "Aucun traitement terminé."
                    else "Aucun traitement en cours.\nAjoutez vos médicaments pour recevoir les rappels de prise.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(list, key = { it.id }) { treatment ->
                        Card(
                            onClick = { onOpenDetail(treatment.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    treatment.drugName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                treatment.dosage?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    if (treatment.endDate == null)
                                        "Depuis le ${Format.dateShort(treatment.startDate)} — continu"
                                    else
                                        "Du ${Format.dateShort(treatment.startDate)} au ${Format.dateShort(treatment.endDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
fun TreatmentDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: TreatmentsViewModel = containerViewModel { TreatmentsViewModel(it) }
    var treatment by remember { mutableStateOf<Treatment?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { treatment = vm.get(id) }
    val t = treatment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t?.drugName ?: "Traitement") },
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
        if (t == null) {
            Column(Modifier.fillMaxSize().padding(padding)) { Text("Traitement introuvable.") }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            t.dosage?.let { DetailRow("Dosage", it) }
            DetailRow("Posologie", buildString {
                t.frequencyLabel?.let { append(it) }
                if (t.intakeTimes.isNotEmpty()) {
                    if (isNotEmpty()) append(" — ")
                    append(t.intakeTimes.joinToString(" · "))
                }
            }.ifBlank { "Non précisée" })
            DetailRow("Début", Format.date(t.startDate))
            DetailRow("Fin", t.endDate?.let { Format.date(it) } ?: "Traitement continu")
            t.prescriber?.let { DetailRow("Prescripteur", it) }
            if (t.prescriptionId != null) DetailRow("Ordonnance liée", "Voir les ordonnances")
            t.notes?.let { DetailRow("Notes", it) }
            Spacer(Modifier.height(16.dp))
            com.medicapp.ui.documents.DocumentsSection(
                owner = com.medicapp.data.db.entity.DocumentOwner.TREATMENT,
                ownerId = id,
                onScan = onScan,
                onOpenDocument = onOpenDocument,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce traitement ?") },
            text = { Text("Les rappels de prise associés cesseront. Cette action est irréversible.") },
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
fun TreatmentFormScreen(id: Long, scanDocumentId: Long? = null, onBack: () -> Unit) {
    val vm: TreatmentsViewModel = containerViewModel { TreatmentsViewModel(it) }
    val prescriptions by vm.prescriptions.collectAsState()

    var existing by remember { mutableStateOf<Treatment?>(null) }
    var profileId by remember { mutableStateOf(0L) }
    var drugName by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var frequencyLabel by rememberSaveable { mutableStateOf("") }
    var intakeTimes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var startDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var continuous by rememberSaveable { mutableStateOf(true) }
    var endDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var prescriber by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var linkedPrescriptionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var prefilledFromScan by remember { mutableStateOf(false) }

    // Pré-remplissage depuis l'ordonnance numérisée (validation manuelle, § 4.3).
    LaunchedEffect(scanDocumentId) {
        if (scanDocumentId != null && id <= 0) {
            val doc = vm.getDocument(scanDocumentId)
            val text = doc?.ocrText
            if (!text.isNullOrBlank()) {
                val parsed = com.medicapp.ocr.OcrFieldParser.parse(text)
                parsed.drugs.firstOrNull()?.let { (name, detectedDosage) ->
                    if (drugName.isBlank()) drugName = name
                    if (detectedDosage != null && dosage.isBlank()) dosage = detectedDosage
                }
                parsed.prescriber?.let { if (prescriber.isBlank()) prescriber = it }
                parsed.mostLikelyDate?.let { if (startDate == null) startDate = it }
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
        val t = existing
        if (!initialized && t != null) {
            drugName = t.drugName
            dosage = t.dosage ?: ""
            frequencyLabel = t.frequencyLabel ?: ""
            intakeTimes = t.intakeTimes
            startDate = t.startDate
            continuous = t.endDate == null
            endDate = t.endDate
            prescriber = t.prescriber ?: ""
            notes = t.notes ?: ""
            linkedPrescriptionId = t.prescriptionId
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "Modifier le traitement" else "Nouveau traitement") },
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
                value = drugName,
                onValueChange = { drugName = it },
                label = { Text("Nom du médicament *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage (ex. 500 mg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FormSectionTitle("Posologie")
            OutlinedTextField(
                value = frequencyLabel,
                onValueChange = { frequencyLabel = it },
                label = { Text("Fréquence (ex. 2 fois par jour)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            IntakeTimesEditor(times = intakeTimes, onChange = { intakeTimes = it })
            FormSectionTitle("Durée")
            DateField(label = "Date de début *", value = startDate, onChange = { startDate = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = continuous, onCheckedChange = { continuous = it })
                Text("Traitement continu")
            }
            if (!continuous) {
                DateField(label = "Date de fin *", value = endDate, onChange = { endDate = it })
            }
            OutlinedTextField(
                value = prescriber,
                onValueChange = { prescriber = it },
                label = { Text("Prescripteur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (prescriptions.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val linked = prescriptions.find { it.prescription.id == linkedPrescriptionId }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = linked?.let { "Ordonnance du ${Format.dateShort(it.prescription.prescriptionDate)}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ordonnance associée (optionnel)") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = {
                            if (linked != null) {
                                IconButton(onClick = { linkedPrescriptionId = null }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Dissocier")
                                }
                            }
                        },
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        prescriptions.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text("Ordonnance du ${Format.dateShort(item.prescription.prescriptionDate)} — ${item.prescription.prescriber ?: "prescripteur inconnu"}")
                                },
                                onClick = {
                                    linkedPrescriptionId = item.prescription.id
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val t = existing
                    vm.save(
                        Treatment(
                            id = t?.id ?: 0L,
                            profileId = t?.profileId ?: profileId,
                            drugName = drugName.trim(),
                            dosage = dosage.trim().ifBlank { null },
                            intakeTimes = intakeTimes,
                            frequencyLabel = frequencyLabel.trim().ifBlank { null },
                            startDate = startDate ?: LocalDate.now(),
                            endDate = if (continuous) null else endDate,
                            prescriber = prescriber.trim().ifBlank { null },
                            prescriptionId = linkedPrescriptionId,
                            notes = notes.trim().ifBlank { null },
                            createdAt = t?.createdAt ?: LocalDateTime.now(),
                        ),
                        attachDocumentId = scanDocumentId,
                        onDone = onBack,
                    )
                },
                enabled = drugName.isNotBlank() && startDate != null && (continuous || endDate != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}

/** Éditeur des heures de prise (notifications de rappel locales, § 4.3). */
@Composable
fun IntakeTimesEditor(times: List<String>, onChange: (List<String>) -> Unit) {
    var newTime by remember { mutableStateOf<LocalTime?>(null) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            times.forEach { time ->
                AssistChip(
                    onClick = { onChange(times - time) },
                    label = { Text(time) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Retirer la prise de $time",
                            modifier = Modifier.height(18.dp),
                        )
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeField(
                label = "Ajouter une prise",
                value = newTime,
                onChange = { newTime = it },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val label = "${newTime!!.hour.toString().padStart(2, '0')}:${newTime!!.minute.toString().padStart(2, '0')}"
                    if (label !in times) onChange((times + label).sorted())
                    newTime = null
                },
                enabled = newTime != null,
            ) { Text("Ajouter") }
        }
        if (times.isNotEmpty()) {
            Text(
                "Rappels de prise : ${times.joinToString(" · ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
