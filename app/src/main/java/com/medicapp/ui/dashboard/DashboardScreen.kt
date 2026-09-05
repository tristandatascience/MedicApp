package com.medicapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.repo.DashboardState
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import com.medicapp.ui.navigation.Routes
import com.medicapp.ui.profiles.CurrentProfileViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.dashboardRepository

    val state = container.settings.settings
        .flatMapLatest { s -> repo.observeDashboard(s.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

/** Tableau de bord : vue synthétique (§ 4.1 du cahier des charges). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModule: (String) -> Unit,
    onManageProfiles: () -> Unit,
) {
    val profileVm: CurrentProfileViewModel = containerViewModel { CurrentProfileViewModel(it) }
    val dashboardVm: DashboardViewModel = containerViewModel { DashboardViewModel(it) }
    val profile by profileVm.currentProfile.collectAsState()
    val state by dashboardVm.state.collectAsState()
    var profileMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Dossier Médical", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { profileMenuOpen = true }) {
                    Text(profile?.name ?: "Aucun profil")
                    Text("  ▾", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                    profileVm.profiles.value.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name + if (p.isSelf) " (moi)" else "") },
                            onClick = {
                                profileVm.switchTo(p.id)
                                profileMenuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Gérer les profils") },
                        onClick = {
                            profileMenuOpen = false
                            onManageProfiles()
                        },
                    )
                }
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Rechercher")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Réglages")
            }
        }

        if (state.badgeCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge { Text("${state.badgeCount}") }
                Spacer(Modifier.size(8.dp))
                Text(
                    "échéance(s) dans les 7 prochains jours",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // --- Prochain rendez-vous ---
        SectionCard(
            title = "Prochain rendez-vous",
            icon = { Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onOpen = { onOpenModule(Routes.APPOINTMENTS) },
        ) {
            val next = state.nextAppointment
            if (next != null) {
                Text(
                    Format.dateTime(next.dateTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(next.professional ?: next.establishment ?: "", style = MaterialTheme.typography.bodyMedium)
                next.specialty?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } else {
                Text("Aucun rendez-vous à venir", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Prises du jour ---
        SectionCard(
            title = "Prises du jour",
            icon = { Icon(Icons.Outlined.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onOpen = { onOpenModule(Routes.TREATMENTS) },
        ) {
            if (state.todaysIntakes.isEmpty()) {
                Text("Aucune prise prévue aujourd'hui", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.todaysIntakes.forEach { intake ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            Format.time(intake.time),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(16.dp))
                        Column {
                            Text(intake.treatment.drugName, style = MaterialTheme.typography.bodyMedium)
                            intake.treatment.dosage?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Vaccinations à venir ---
        SectionCard(
            title = "Rappels de vaccination",
            icon = { Icon(Icons.Outlined.Vaccines, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onOpen = { onOpenModule(Routes.VACCINATIONS) },
        ) {
            if (state.upcomingBoosters.isEmpty()) {
                Text("Aucun rappel dans les 7 prochains jours", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.upcomingBoosters.take(3).forEach { vaccination ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(vaccination.vaccineName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            vaccination.nextDueDate?.let { Format.dateShort(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Accès rapide aux modules ---
        Text("Mes modules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                listOf(
                    "Vaccinations" to Routes.VACCINATIONS,
                    "Traitements" to Routes.TREATMENTS,
                    "Ordonnances" to Routes.PRESCRIPTIONS,
                    "Examens" to Routes.EXAMS,
                    "Rendez-vous" to Routes.APPOINTMENTS,
                )
            ) { (label, route) ->
                Card(onClick = { onOpenModule(route) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🗂", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: @Composable () -> Unit,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
