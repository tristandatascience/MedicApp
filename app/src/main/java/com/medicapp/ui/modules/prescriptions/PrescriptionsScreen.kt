package com.medicapp.ui.modules.prescriptions

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.medicapp.data.db.dao.PrescriptionWithMedicines
import com.medicapp.data.db.entity.Prescription
import com.medicapp.data.db.entity.PrescriptionMedicine
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.DateField
import com.medicapp.ui.common.DetailRow
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.FormSectionTitle
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PrescriptionsViewModel(private val container: AppContainer) : ViewModel() {

    val list = container.settings.settings
        .flatMapLatest { container.prescriptionRepository.observeForProfile(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): PrescriptionWithMedicines? = container.prescriptionRepository.getById(id)

    suspend fun currentProfileId(): Long = container.settings.current().currentProfileId

    fun save(prescription: Prescription, medicines: List<PrescriptionMedicine>, onDone: () -> Unit) {
        viewModelScope.launch {
            container.prescriptionRepository.save(prescription, medicines)
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.prescriptionRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionsScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenForm: (Long) -> Unit = {},
) {
    val vm: PrescriptionsViewModel = containerViewModel { PrescriptionsViewModel(it) }
    val list by vm.list.collectAsState()
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ordonnances") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(-1L) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter une ordonnance")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (list.isEmpty()) {
                Text(
                    "Aucune ordonnance enregistrée.\nNumérisez vos ordonnances pour les conserver et suivre leur validité.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(list, key = { it.prescription.id }) { item ->
                        val p = item.prescription
                        val expired = p.expiryDate != null && p.expiryDate!!.isBefore(today)
                        Card(
                            onClick = { onOpenDetail(p.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = if (expired) {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            } else {
                                CardDefaults.cardColors()
                            },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Ordonnance du ${Format.dateShort(p.prescriptionDate)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    listOfNotNull(p.prescriber, p.specialty).joinToString(" — ").ifBlank { "Prescripteur non renseigné" },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${item.medicines.size} médicament(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                p.expiryDate?.let { expiry ->
                                    Text(
                                        if (expired) "⚠ Expire le ${Format.dateShort(expiry)}"
                                        else "Valide jusqu'au ${Format.dateShort(expiry)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (expired) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: PrescriptionsViewModel = containerViewModel { PrescriptionsViewModel(it) }
    var item by remember { mutableStateOf<PrescriptionWithMedicines?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { item = vm.get(id) }

    val p = item?.prescription
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ordonnance") },
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
        if (p == null) {
            Column(Modifier.fillMaxSize().padding(padding)) { Text("Ordonnance introuvable.") }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            p.expiryDate?.let { expiry ->
                val expired = expiry.isBefore(today)
                if (expired) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Text(
                            "⚠ Cette ordonnance a expiré le ${Format.dateShort(expiry)}.",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            DetailRow("Date de prescription", Format.date(p.prescriptionDate))
            p.prescriber?.let { DetailRow("Prescripteur", it) }
            p.specialty?.let { DetailRow("Spécialité", it) }
            p.validityDays?.let {
                DetailRow("Validité", "$it jours (jusqu'au ${Format.dateShort(p.prescriptionDate.plusDays(it.toLong()))})")
            } ?: DetailRow("Validité", "12 mois (durée par défaut)")
            p.notes?.let { DetailRow("Notes", it) }
            FormSectionTitle("Médicaments prescrits")
            item?.medicines?.sortedBy { it.position }?.forEach { medicine ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(medicine.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    listOfNotNull(medicine.dosage, medicine.duration).joinToString(" — ").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            DetailRow("Document numérisé", if (p.documentId != null) "Voir le document" else "Aucun document numérisé")
            Spacer(Modifier.height(16.dp))
            com.medicapp.ui.documents.DocumentsSection(
                owner = com.medicapp.data.db.entity.DocumentOwner.PRESCRIPTION,
                ownerId = id,
                onScan = onScan,
                onOpenDocument = onOpenDocument,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette ordonnance ?") },
            text = { Text("Les traitements liés seront conservés mais dissociés. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id); confirmDelete = false; onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private data class MedicineDraft(
    val name: String = "",
    val dosage: String = "",
    val duration: String = "",
) : java.io.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionFormScreen(id: Long, onBack: () -> Unit) {
    val vm: PrescriptionsViewModel = containerViewModel { PrescriptionsViewModel(it) }

    var existing by remember { mutableStateOf<PrescriptionWithMedicines?>(null) }
    var profileId by remember { mutableStateOf(0L) }
    var prescriptionDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var prescriber by rememberSaveable { mutableStateOf("") }
    var specialty by rememberSaveable { mutableStateOf("") }
    var validityDays by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var medicines by rememberSaveable { mutableStateOf(listOf<MedicineDraft>()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id > 0) existing = vm.get(id)
        profileId = existing?.prescription?.profileId ?: vm.currentProfileId()
    }
    LaunchedEffect(existing) {
        val item = existing
        if (!initialized && item != null) {
            val p = item.prescription
            prescriptionDate = p.prescriptionDate
            prescriber = p.prescriber ?: ""
            specialty = p.specialty ?: ""
            validityDays = p.validityDays?.toString() ?: ""
            notes = p.notes ?: ""
            medicines = item.medicines.sortedBy { it.position }.map {
                MedicineDraft(it.name, it.dosage ?: "", it.duration ?: "")
            }
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "Modifier l'ordonnance" else "Nouvelle ordonnance") },
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
            DateField(label = "Date de prescription *", value = prescriptionDate, onChange = { prescriptionDate = it })
            OutlinedTextField(
                value = prescriber,
                onValueChange = { prescriber = it },
                label = { Text("Prescripteur (ex. Dr Martin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = specialty,
                onValueChange = { specialty = it },
                label = { Text("Spécialité") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = validityDays,
                onValueChange = { validityDays = it.filter { c -> c.isDigit() } },
                label = { Text("Durée de validité en jours (vide = 12 mois)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FormSectionTitle("Médicaments prescrits")
            medicines.forEachIndexed { index, draft ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { value ->
                                medicines = medicines.toMutableList().also { list -> list[index] = draft.copy(name = value) }
                            },
                            label = { Text("Médicament ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.dosage,
                                onValueChange = { value ->
                                    medicines = medicines.toMutableList().also { list -> list[index] = draft.copy(dosage = value) }
                                },
                                label = { Text("Dosage") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = draft.duration,
                                onValueChange = { value ->
                                    medicines = medicines.toMutableList().also { list -> list[index] = draft.copy(duration = value) }
                                },
                                label = { Text("Durée") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(onClick = { medicines = medicines.filterIndexed { i, _ -> i != index } }) {
                            Text("Retirer")
                        }
                    }
                }
            }
            OutlinedButton(text = "Ajouter un médicament", onClick = {
                medicines = medicines + MedicineDraft()
            })

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val p = existing?.prescription
                    vm.save(
                        Prescription(
                            id = p?.id ?: 0L,
                            profileId = p?.profileId ?: profileId,
                            prescriptionDate = prescriptionDate ?: LocalDate.now(),
                            prescriber = prescriber.trim().ifBlank { null },
                            specialty = specialty.trim().ifBlank { null },
                            validityDays = validityDays.toIntOrNull(),
                            documentId = p?.documentId,
                            notes = notes.trim().ifBlank { null },
                            createdAt = p?.createdAt ?: LocalDateTime.now(),
                        ),
                        medicines = medicines
                            .filter { it.name.isNotBlank() }
                            .mapIndexed { index, draft ->
                                PrescriptionMedicine(
                                    id = 0L,
                                    prescriptionId = p?.id ?: 0L,
                                    name = draft.name.trim(),
                                    dosage = draft.dosage.trim().ifBlank { null },
                                    duration = draft.duration.trim().ifBlank { null },
                                    position = index,
                                )
                            },
                        onDone = onBack,
                    )
                },
                enabled = prescriptionDate != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}

@Composable
private fun OutlinedButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.height(4.dp))
        Text(text)
    }
}
