package com.medicapp.ui.modules.appointments

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
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
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.Appointment
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
import java.time.ZoneId

private val DOCUMENTS_TO_BRING_SUGGESTIONS = listOf(
    "Carte vitale",
    "Carte d'identité",
    "Ordonnance en cours",
    "Derniers résultats d'analyses",
    "Radiographies / imagerie",
    "Carnet de vaccination",
    "Liste des médicaments",
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModel(private val container: AppContainer) : ViewModel() {

    val upcoming = container.settings.settings
        .flatMapLatest { container.appointmentRepository.observeUpcoming(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val past = container.settings.settings
        .flatMapLatest { container.appointmentRepository.observePast(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Appointment? = container.appointmentRepository.getById(id)

    suspend fun getDocument(id: Long) = container.documentRepository.getById(id)

    suspend fun currentProfileId(): Long = container.settings.current().currentProfileId

    fun save(appointment: Appointment, attachDocumentId: Long? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = container.appointmentRepository.upsert(appointment)
            attachDocumentId?.let {
                container.documentRepository.reassign(
                    it,
                    com.medicapp.data.db.entity.DocumentOwner.APPOINTMENT,
                    id,
                )
            }
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.appointmentRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenForm: (Long) -> Unit = {},
    onScan: () -> Unit = {},
) {
    val vm: AppointmentsViewModel = containerViewModel { AppointmentsViewModel(it) }
    val upcoming by vm.upcoming.collectAsState()
    val past by vm.past.collectAsState()
    var showPast by remember { mutableStateOf(false) }
    val list = if (showPast) past else upcoming

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rendez-vous") },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(
                            Icons.Outlined.DocumentScanner,
                            contentDescription = "Numériser un document à apporter",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(-1L) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter un rendez-vous")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showPast, onClick = { showPast = false }, label = { Text("À venir") })
                FilterChip(selected = showPast, onClick = { showPast = true }, label = { Text("Historique") })
            }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Text(
                    if (showPast) "Aucun rendez-vous passé."
                    else "Aucun rendez-vous à venir.\nAjoutez vos consultations pour recevoir les rappels.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(list, key = { it.id }) { appointment ->
                        Card(
                            onClick = { onOpenDetail(appointment.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    appointment.professional ?: appointment.establishment ?: "Rendez-vous",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    Format.dateTime(appointment.dateTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                appointment.specialty?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                appointment.reason?.let {
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
fun AppointmentDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onScan: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: AppointmentsViewModel = containerViewModel { AppointmentsViewModel(it) }
    val context = LocalContext.current
    var appointment by remember { mutableStateOf<Appointment?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { appointment = vm.get(id) }
    val a = appointment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rendez-vous") },
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
        if (a == null) {
            Column(Modifier.fillMaxSize().padding(padding)) { Text("Rendez-vous introuvable.") }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            DetailRow("Date et heure", Format.dateTime(a.dateTime))
            a.professional?.let { DetailRow("Professionnel", it) }
            a.establishment?.let { DetailRow("Établissement", it) }
            a.specialty?.let { DetailRow("Spécialité", it) }
            a.address?.let { DetailRow("Adresse", it) }
            a.reason?.let { DetailRow("Motif", it) }
            if (a.documentsToBring.isNotEmpty()) {
                FormSectionTitle("Documents à apporter")
                a.documentsToBring.forEach { doc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = true, onCheckedChange = null)
                        Text(doc)
                    }
                }
            }
            a.notes?.let { DetailRow("Notes", it) }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { addPhoneCalendarEvent(context, a) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Ajouter au calendrier du téléphone")
            }
            Spacer(Modifier.height(16.dp))
            com.medicapp.ui.documents.DocumentsSection(
                owner = com.medicapp.data.db.entity.DocumentOwner.APPOINTMENT,
                ownerId = id,
                onScan = onScan,
                onOpenDocument = onOpenDocument,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce rendez-vous ?") },
            text = { Text("Le rendez-vous et ses rappels seront supprimés. Action irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id); confirmDelete = false; onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Ouverture de l'application Calendrier pré-remplie (aucune permission requise). */
private fun addPhoneCalendarEvent(context: Context, appointment: Appointment) {
    val begin = appointment.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60L * 60_000)
        .putExtra(
            CalendarContract.Events.TITLE,
            "RDV ${appointment.professional ?: appointment.establishment ?: "médical"}",
        )
        .putExtra(CalendarContract.Events.EVENT_LOCATION, appointment.address ?: "")
        .putExtra(CalendarContract.Events.DESCRIPTION, appointment.reason ?: "")
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Aucune application de calendrier disponible : silencieux.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentFormScreen(id: Long, scanDocumentId: Long? = null, onBack: () -> Unit) {
    val vm: AppointmentsViewModel = containerViewModel { AppointmentsViewModel(it) }

    var existing by remember { mutableStateOf<Appointment?>(null) }
    var profileId by remember { mutableStateOf(0L) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var professional by rememberSaveable { mutableStateOf("") }
    var establishment by rememberSaveable { mutableStateOf("") }
    var specialty by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var documentsToBring by rememberSaveable { mutableStateOf(listOf<String>()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var prefilledFromScan by remember { mutableStateOf(false) }

    // Pré-remplissage depuis un document numérisé (convocation…), validation manuelle.
    LaunchedEffect(scanDocumentId) {
        if (scanDocumentId != null && id <= 0) {
            val doc = vm.getDocument(scanDocumentId)
            val text = doc?.ocrText
            if (!text.isNullOrBlank()) {
                val parsed = com.medicapp.ocr.OcrFieldParser.parse(text)
                parsed.mostLikelyDate?.let { if (date == null) date = it }
                parsed.times.firstOrNull()?.let { if (time == null) time = it }
                parsed.prescriber?.let { if (professional.isBlank()) professional = it }
                parsed.laboratory?.let { if (establishment.isBlank()) establishment = it }
                prefilledFromScan = true
            }
        }
    }

    LaunchedEffect(id) {
        if (id > 0) existing = vm.get(id)
        profileId = existing?.profileId ?: vm.currentProfileId()
    }
    LaunchedEffect(existing) {
        val a = existing
        if (!initialized && a != null) {
            date = a.dateTime.toLocalDate()
            time = a.dateTime.toLocalTime()
            professional = a.professional ?: ""
            establishment = a.establishment ?: ""
            specialty = a.specialty ?: ""
            address = a.address ?: ""
            reason = a.reason ?: ""
            documentsToBring = a.documentsToBring
            notes = a.notes ?: ""
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "Modifier le rendez-vous" else "Nouveau rendez-vous") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "Date *", value = date, onChange = { date = it }, modifier = Modifier.weight(1f))
                TimeField(label = "Heure *", value = time, onChange = { time = it }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = professional,
                onValueChange = { professional = it },
                label = { Text("Professionnel (ex. Dr Martin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = establishment,
                onValueChange = { establishment = it },
                label = { Text("Établissement") },
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
                value = address,
                onValueChange = { address = it },
                label = { Text("Adresse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motif") },
                modifier = Modifier.fillMaxWidth(),
            )

            FormSectionTitle("Documents à apporter")
            DOCUMENTS_TO_BRING_SUGGESTIONS.forEach { suggestion ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = suggestion in documentsToBring,
                        onCheckedChange = { checked ->
                            documentsToBring =
                                if (checked) documentsToBring + suggestion
                                else documentsToBring - suggestion
                        },
                    )
                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes libres") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val a = existing
                    vm.save(
                        Appointment(
                            id = a?.id ?: 0L,
                            profileId = a?.profileId ?: profileId,
                            dateTime = LocalDateTime.of(date ?: LocalDate.now(), time ?: LocalTime.of(9, 0)),
                            professional = professional.trim().ifBlank { null },
                            establishment = establishment.trim().ifBlank { null },
                            specialty = specialty.trim().ifBlank { null },
                            address = address.trim().ifBlank { null },
                            reason = reason.trim().ifBlank { null },
                            documentsToBring = documentsToBring,
                            notes = notes.trim().ifBlank { null },
                            createdAt = a?.createdAt ?: LocalDateTime.now(),
                        ),
                        attachDocumentId = scanDocumentId,
                        onDone = onBack,
                    )
                },
                enabled = date != null && time != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}
