package com.medicapp.ui.modules.vaccinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.medicapp.data.db.entity.Vaccination
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.DateField
import com.medicapp.ui.common.DetailRow
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Suggestions issues du calendrier vaccinal français (§ 4.2). */
val FRENCH_VACCINE_SUGGESTIONS: List<Pair<String, String>> = listOf(
    "DTP" to "Diphtérie, tétanos, poliomyélite",
    "Coqueluche" to "Coqueluche",
    "Hib" to "Haemophilus influenzae de type b",
    "Hépatite B" to "Hépatite B",
    "Pneumocoque" to "Infections invasives à pneumocoque",
    "Méningocoque C" to "Infections invasives à méningocoque C",
    "Méningocoque B" to "Infections invasives à méningocoque B",
    "ROR" to "Rougeole, oreillons, rubéole",
    "HPV" to "Papillomavirus",
    "Grippe" to "Grippe saisonnière",
    "Zona" to "Zona",
    "Rotavirus" to "Gastro-entérites à rotavirus",
    "Hépatite A" to "Hépatite A",
    "BCG" to "Tuberculose",
    "Fièvre jaune" to "Fièvre jaune",
    "Rage" to "Rage (après exposition)",
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaccinationsViewModel(private val container: AppContainer) : ViewModel() {

    val list = container.settings.settings
        .flatMapLatest { container.vaccinationRepository.observeForProfile(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Vaccination? = container.vaccinationRepository.getById(id)

    suspend fun currentProfileId(): Long = container.settings.current().currentProfileId

    fun save(vaccination: Vaccination, onDone: () -> Unit) {
        viewModelScope.launch {
            container.vaccinationRepository.upsert(vaccination)
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.vaccinationRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationsScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenForm: (Long) -> Unit = {},
) {
    val vm: VaccinationsViewModel = containerViewModel { VaccinationsViewModel(it) }
    val list by vm.list.collectAsState()
    val today = remember { LocalDate.now() }
    var onlyUpcoming by remember { mutableStateOf(false) }

    val filtered = if (onlyUpcoming) {
        list.filter { it.nextDueDate != null && !it.nextDueDate!!.isBefore(today) }
    } else list

    Scaffold(
        topBar = { TopAppBar(title = { Text("Vaccinations") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(-1L) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter une vaccination")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !onlyUpcoming,
                    onClick = { onlyUpcoming = false },
                    label = { Text("Chronologique") },
                )
                FilterChip(
                    selected = onlyUpcoming,
                    onClick = { onlyUpcoming = true },
                    label = { Text("Rappels à venir") },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    "Aucune vaccination enregistrée.\nAjoutez vos vaccins ou numérisez votre carnet de vaccination.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { vaccination ->
                        Card(
                            onClick = { onOpenDetail(vaccination.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    vaccination.vaccineName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    vaccination.disease,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        "Injecté le ${Format.dateShort(vaccination.injectionDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    vaccination.nextDueDate?.let {
                                        Text(
                                            "Rappel : ${Format.dateShort(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (!it.isBefore(today)) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.error,
                                        )
                                    }
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
fun VaccinationDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: VaccinationsViewModel = containerViewModel { VaccinationsViewModel(it) }
    var vaccination by remember { mutableStateOf<Vaccination?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(id) {
        vaccination = vm.get(id)
    }

    val v = vaccination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(v?.vaccineName ?: "Vaccination") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Supprimer")
                    }
                },
            )
        },
    ) { padding ->
        if (v == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text("Vaccination introuvable.")
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DetailRow("Maladie ciblée", v.disease)
            DetailRow("Date d'injection", Format.date(v.injectionDate))
            v.lotNumber?.let { DetailRow("Numéro de lot", it) }
            v.provider?.let { DetailRow("Professionnel / centre", it) }
            v.nextDueDate?.let {
                DetailRow(
                    "Rappel prévu",
                    Format.date(it) + " (${Format.relativeFromToday(it, LocalDate.now())})",
                )
            }
            v.notes?.let { DetailRow("Notes", it) }
            Spacer(Modifier.height(16.dp))
            com.medicapp.ui.documents.DocumentsSection(
                owner = com.medicapp.data.db.entity.DocumentOwner.VACCINATION,
                ownerId = id,
                onScan = onScan,
                onOpenDocument = onOpenDocument,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette vaccination ?") },
            text = { Text("Cette fiche et son éventuel document numérisé seront supprimés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    confirmDelete = false
                    onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationFormScreen(
    id: Long,
    onBack: () -> Unit,
) {
    val vm: VaccinationsViewModel = containerViewModel { VaccinationsViewModel(it) }
    val profileId = remember { mutableStateOf(0L) }
    var existing by remember { mutableStateOf<Vaccination?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        if (id > 0) existing = vm.get(id)
        if (id <= 0 || profileId.value == 0L) {
            val fromExisting = existing?.profileId
            profileId.value = fromExisting ?: vm.currentProfileId()
        }
    }

    var vaccineName by rememberSaveable { mutableStateOf("") }
    var disease by rememberSaveable { mutableStateOf("") }
    var injectionDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var lotNumber by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var nextDueDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(existing) {
        val v = existing
        if (!initialized && v != null) {
            vaccineName = v.vaccineName
            disease = v.disease
            injectionDate = v.injectionDate
            lotNumber = v.lotNumber ?: ""
            provider = v.provider ?: ""
            nextDueDate = v.nextDueDate
            notes = v.notes ?: ""
            profileId.value = v.profileId
            initialized = true
        }
    }

    var suggestionsExpanded by remember { mutableStateOf(false) }
    val filteredSuggestions = FRENCH_VACCINE_SUGGESTIONS.filter {
        it.first.contains(vaccineName.trim(), ignoreCase = true) && vaccineName.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "Modifier la vaccination" else "Nouvelle vaccination") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                onExpandedChange = { suggestionsExpanded = it },
            ) {
                OutlinedTextField(
                    value = vaccineName,
                    onValueChange = { vaccineName = it },
                    label = { Text("Nom du vaccin *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(
                    expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { suggestionsExpanded = false },
                ) {
                    filteredSuggestions.forEach { (name, target) ->
                        DropdownMenuItem(
                            text = { Text("$name — $target") },
                            onClick = {
                                vaccineName = name
                                if (disease.isBlank()) disease = target
                                suggestionsExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = disease,
                onValueChange = { disease = it },
                label = { Text("Maladie ciblée *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(label = "Date d'injection *", value = injectionDate, onChange = { injectionDate = it })
            OutlinedTextField(
                value = lotNumber,
                onValueChange = { lotNumber = it },
                label = { Text("Numéro de lot (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Professionnel / centre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(label = "Rappel prévu le (optionnel)", value = nextDueDate, onChange = { nextDueDate = it })
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            androidx.compose.material3.Button(
                onClick = {
                    val v = existing
                    vm.save(
                        Vaccination(
                            id = v?.id ?: 0L,
                            profileId = v?.profileId ?: profileId.value,
                            vaccineName = vaccineName.trim(),
                            disease = disease.trim(),
                            injectionDate = injectionDate ?: LocalDate.now(),
                            lotNumber = lotNumber.trim().ifBlank { null },
                            provider = provider.trim().ifBlank { null },
                            nextDueDate = nextDueDate,
                            documentId = v?.documentId,
                            notes = notes.trim().ifBlank { null },
                            createdAt = v?.createdAt ?: java.time.LocalDateTime.now(),
                        ),
                        onDone = onBack,
                    )
                },
                enabled = vaccineName.isNotBlank() && disease.isNotBlank() && injectionDate != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}
