package com.medicapp.ui.profiles

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicapp.data.db.entity.Profile
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Gestion des profils multiples (§ 4.9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen() {
    val vm: ProfilesViewModel = containerViewModel { ProfilesViewModel(it) }
    val profiles by vm.profiles.collectAsState()
    val currentId by vm.currentProfileId.collectAsState()

    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var deleteProfile by remember { mutableStateOf<Profile?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profils") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Ajouter un profil")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aucun profil", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Créez un profil pour chaque membre de la famille.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        onClick = { vm.switchTo(profile.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = if (profile.id == currentId) {
                            androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            androidx.compose.material3.CardDefaults.cardColors()
                        },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.name + if (profile.isSelf) " (moi)" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                profile.birthDate?.let {
                                    Text(
                                        "Né(e) le ${Format.dateShort(it)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (profile.id == currentId) {
                                    Text("Profil actif", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            IconButton(onClick = { editProfile = profile }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Renommer")
                            }
                            IconButton(onClick = { deleteProfile = profile }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Supprimer")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ProfileFormDialog(
            title = "Nouveau profil",
            initialName = "",
            initialBirthDate = null,
            initialIsSelf = false,
            onDismiss = { showCreate = false },
            onConfirm = { name, birth, isSelf ->
                vm.create(name, birth, isSelf)
                showCreate = false
            },
        )
    }

    editProfile?.let { profile ->
        ProfileFormDialog(
            title = "Modifier le profil",
            initialName = profile.name,
            initialBirthDate = profile.birthDate,
            initialIsSelf = profile.isSelf,
            onDismiss = { editProfile = null },
            onConfirm = { name, _, isSelf ->
                vm.rename(profile, name)
                editProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text("Supprimer le profil « ${profile.name} » ?") },
            text = {
                Text(
                    "Toutes ses vaccinations, traitements, ordonnances, examens et rendez-vous " +
                        "seront définitivement supprimés. Cette action est irréversible."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(profile)
                    deleteProfile = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfile = null }) { Text("Annuler") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFormDialog(
    title: String,
    initialName: String,
    initialBirthDate: LocalDate?,
    initialIsSelf: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, birthDate: LocalDate?, isSelf: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }
    var isSelf by remember { mutableStateOf(initialIsSelf) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = if (birthDate != null) Format.dateShort(birthDate) else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date de naissance (optionnelle)") },
                    trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Choisir") } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSelf, onCheckedChange = { isSelf = it })
                    Text("Ce profil me concerne")
                }
                if (showDatePicker) {
                    val pickerState = androidx.compose.material3.rememberDatePickerState()
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let {
                                    birthDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
                        },
                    ) {
                        androidx.compose.material3.DatePicker(state = pickerState)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, birthDate, isSelf) }, enabled = name.isNotBlank()) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
