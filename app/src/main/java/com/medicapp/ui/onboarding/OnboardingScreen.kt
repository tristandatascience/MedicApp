package com.medicapp.ui.onboarding

import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.data.storage.StorageMode
import com.medicapp.ui.LocalAppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.PinDots
import com.medicapp.ui.common.PinPad

/**
 * Assistant de première utilisation : bienvenue, choix du mode de stockage
 * (§ 5 du cahier des charges, en langage simple avec comparatif), création du
 * code PIN, biométrie optionnelle et création du premier profil.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingViewModel(container) } }
    )
    val step by vm.step.collectAsState()
    val totalSteps = OnboardingStep.entries.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / totalSteps },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onNext = vm::next)
            OnboardingStep.STORAGE -> StorageStep(onChoose = vm::chooseStorage)
            OnboardingStep.PIN_LENGTH -> PinLengthStep(onChoose = vm::choosePinLength)
            OnboardingStep.PIN_ENTER, OnboardingStep.PIN_CONFIRM -> PinEntryStep(vm)
            OnboardingStep.BIOMETRIC -> BiometricStep(vm)
            OnboardingStep.PROFILE -> ProfileStep(vm)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Icon(
        Icons.Outlined.HealthAndSafety,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(64.dp),
    )
    Spacer(Modifier.height(16.dp))
    StepTitle("Bienvenue dans votre dossier médical")
    Spacer(Modifier.height(12.dp))
    Text(
        "Centralisez vos vaccinations, traitements, ordonnances, résultats " +
            "d'examens et rendez-vous. Numérisez vos documents papier et " +
            "retrouvez-les grâce à la recherche.\n\n" +
            "Vos données restent sous votre contrôle : rien n'est envoyé à un serveur tiers.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Commencer") }
}

@Composable
private fun StorageStep(onChoose: (StorageMode) -> Unit) {
    StepTitle("Où conserver vos données ?")
    Spacer(Modifier.height(8.dp))
    Text(
        "Ce choix peut être modifié plus tard dans les réglages.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sur ce téléphone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "• Vos données sont chiffrées (AES-256) et ne quittent jamais votre téléphone.\n" +
                    "• L'application fonctionne entièrement hors ligne, même en mode avion.\n" +
                    "• Une sauvegarde chiffrée par mot de passe est disponible en cas de changement de téléphone.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { onChoose(StorageMode.LOCAL) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choisir le stockage local")
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mon Google Drive personnel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Bientôt disponible",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "• Vos données chiffrées seraient hébergées par Google sur votre propre compte Drive.\n" +
                    "• Même chiffrées, elles seraient stockées chez Google : cela a des implications " +
                    "que vous devez accepter en connaissance de cause.\n" +
                    "• Synchronisation entre vos appareils via votre compte.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PinLengthStep(onChoose: (Int) -> Unit) {
    StepTitle("Choisissez votre code PIN")
    Spacer(Modifier.height(8.dp))
    Text(
        "Ce code protège l'accès à votre dossier médical. Il vous sera demandé à chaque ouverture.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))
    listOf(4, 5, 6).forEach { length ->
        OutlinedButton(onClick = { onChoose(length) }, modifier = Modifier.fillMaxWidth()) {
            Text("$length chiffres")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PinEntryStep(vm: OnboardingViewModel) {
    val step by vm.step.collectAsState()
    val entered by vm.entered.collectAsState()
    val pinLength by vm.pinLength.collectAsState()
    val error by vm.error.collectAsState()

    StepTitle(if (step == OnboardingStep.PIN_ENTER) "Définissez votre code" else "Confirmez votre code")
    Spacer(Modifier.height(8.dp))
    if (error != null) {
        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(16.dp))
    PinDots(filled = entered.length, total = pinLength)
    Spacer(Modifier.height(24.dp))
    PinPad(onDigit = vm::onDigit, onBackspace = vm::onBackspace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiometricStep(vm: OnboardingViewModel) {
    val context = LocalContext.current
    val canBiometric = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Icon(
        Icons.Outlined.Fingerprint,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(16.dp))
    StepTitle("Déverrouillage biométrique")
    Spacer(Modifier.height(8.dp))
    if (canBiometric) {
        Text(
            "Pour ouvrir plus vite votre dossier, utilisez votre empreinte ou votre visage " +
                "à la place du code PIN. Vous pourrez changer cela dans les réglages.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.setBiometric(true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Activer la biométrie")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.setBiometric(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Plus tard")
        }
    } else {
        Text(
            "Votre téléphone ne propose pas de déverrouillage biométrique : le code PIN sera utilisé.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.setBiometric(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Continuer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileStep(vm: OnboardingViewModel) {
    val name by vm.profileName.collectAsState()
    val birthDate by vm.profileBirthDate.collectAsState()
    val isSelf by vm.profileIsSelf.collectAsState()
    val error by vm.error.collectAsState()
    val creating by vm.creating.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    Icon(
        Icons.Outlined.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(16.dp))
    StepTitle("Créez votre premier profil")
    Spacer(Modifier.height(8.dp))
    Text(
        "Vous pourrez ensuite ajouter d'autres profils (enfants, proches) depuis l'accueil.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = name,
        onValueChange = vm::setProfileName,
        label = { Text("Nom du profil (ex. Camille)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = Format.dateShort(birthDate).takeIf { birthDate != null } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text("Date de naissance (optionnelle)") },
        trailingIcon = {
            TextButton(onClick = { showDatePicker = true }) { Text("Choisir") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isSelf, onCheckedChange = vm::setProfileIsSelf)
        Text("Ce profil me concerne")
    }
    if (error != null) {
        Text(error!!, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { vm.finish(onDone = {}) }, enabled = !creating, modifier = Modifier.fillMaxWidth()) {
        Text(if (creating) "Création…" else "Créer mon dossier")
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { vm.setProfileBirthDate(vm.millisToLocalDate(it)) }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
