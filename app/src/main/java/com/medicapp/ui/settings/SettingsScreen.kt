package com.medicapp.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.prefs.AppSettings
import com.medicapp.data.storage.StorageMode
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.PinDots
import com.medicapp.ui.common.PinPad
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBiometric(enabled: Boolean) = launch { container.settings.setBiometricEnabled(enabled) }
    fun setAutoLock(minutes: Int) = launch { container.settings.setAutoLockMinutes(minutes) }
    fun setFlagSecure(enabled: Boolean) = launch { container.settings.setFlagSecure(enabled) }
    fun setDynamicColors(enabled: Boolean) = launch { container.settings.setDynamicColors(enabled) }

    suspend fun verifyPin(pin: String): Boolean = container.settings.verifyPin(pin)

    fun changePin(currentPin: String, newPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = container.settings.verifyPin(currentPin)
            if (ok) container.settings.setPin(newPin)
            onResult(ok)
        }
    }

    /** Archive chiffrée par mot de passe, à conserver hors du téléphone (§ 5.1). */
    fun exportBackup(context: Context, uri: android.net.Uri, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                com.medicapp.data.backup.BackupManager(context, container)
                    .exportTo(uri, password) { }
            }.isSuccess
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun importBackup(context: Context, uri: android.net.Uri, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                com.medicapp.data.backup.BackupManager(context, container)
                    .importFrom(uri, password) { }
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** Recherche ponctuelle de mise à jour (requête anonyme vers GitHub). */
    fun checkUpdate(onResult: (info: com.medicapp.updates.UpdateChecker.ReleaseInfo?, error: String?) -> Unit) {
        viewModelScope.launch {
            val outcome = com.medicapp.updates.UpdateChecker.checkForUpdate()
            withContext(Dispatchers.Main) { onResult(outcome.info, outcome.error) }
        }
    }

    /** Suppression définitive de l'ensemble du dossier (§ 6, droit à l'effacement). */
    fun wipeAllData(context: Context) {
        viewModelScope.launch {
            container.resetDatabase()
            container.settings.wipe()
            val dir = context.filesDir
            java.io.File(dir, ".mk").delete()
            java.io.File(dir, "documents").deleteRecursively()
            java.io.File(dir, "datastore").deleteRecursively()
            val dbDir = context.getDatabasePath("medic.db").parentFile
            dbDir?.listFiles()?.forEach { it.delete() }
            container.appLock.lock()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val vm: SettingsViewModel = containerViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var showChangePin by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var showExportPassword by remember { mutableStateOf<android.net.Uri?>(null) }
    var showImportPassword by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    // --- Mise à jour ---
    var updateChecking by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<com.medicapp.updates.UpdateChecker.ReleaseInfo?>(null) }
    var updateUpToDate by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var updateReady by remember { mutableStateOf(false) }
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    DisposableEffect(downloadId) {
        val receiver = downloadId?.let { id ->
            com.medicapp.updates.ApkInstaller.registerCompletionReceiver(context, id) {
                updateReady = true
            }
        }
        onDispose { receiver?.let { context.unregisterReceiver(it) } }
    }

    // --- Moteur IA embarqué (optionnel, à la demande) ---
    val aiEngine = com.medicapp.ui.LocalAppContainer.current.gemmaEngine
    var aiInstalled by remember { mutableStateOf(aiEngine.isInstalled()) }
    var aiDownloadId by remember { mutableStateOf<Long?>(null) }
    var showDeleteAi by remember { mutableStateOf(false) }

    DisposableEffect(aiDownloadId) {
        val receiver = aiDownloadId?.let { id ->
            com.medicapp.updates.ApkInstaller.registerCompletionReceiver(context, id) {
                aiInstalled = aiEngine.isInstalled()
                if (aiInstalled) {
                    Toast.makeText(context, "Moteur IA installé", Toast.LENGTH_SHORT).show()
                }
            }
        }
        onDispose { receiver?.let { context.unregisterReceiver(it) } }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) showExportPassword = uri }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) showImportPassword = uri }

    val restartApp: () -> Unit = {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    val s = settings ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
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
                .padding(horizontal = 16.dp),
        ) {
            SettingsSection("Sécurité")
            SettingRow(
                title = "Changer le code PIN",
                subtitle = "Modifie le code de déverrouillage",
                onClick = { showChangePin = true },
            )
            SwitchRow(
                title = "Déverrouillage biométrique",
                subtitle = "Empreinte ou reconnaissance faciale",
                checked = s.biometricEnabled,
                onCheckedChange = vm::setBiometric,
            )
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Verrouillage automatique : ${s.autoLockMinutes} min d'inactivité")
                Slider(
                    value = s.autoLockMinutes.toFloat(),
                    onValueChange = { vm.setAutoLock(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }
            SwitchRow(
                title = "Bloquer les captures d'écran",
                subtitle = "Empêche la capture des écrans sensibles",
                checked = s.flagSecure,
                onCheckedChange = vm::setFlagSecure,
            )

            SettingsSection("Stockage")
            Text(
                when (s.storageMode) {
                    StorageMode.LOCAL ->
                        "Mode actuel : stockage local chiffré.\n" +
                            "Vos données ne quittent jamais ce téléphone (AES-256)."
                    StorageMode.DRIVE ->
                        "Mode actuel : Google Drive (non disponible dans cette version)."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            SettingsSection("Apparence")
            SwitchRow(
                title = "Couleurs dynamiques",
                subtitle = "Suivre le thème du système (Android 12+)",
                checked = s.dynamicColors,
                onCheckedChange = vm::setDynamicColors,
            )

            SettingsSection("Données")
            SettingRow(
                title = "Exporter une sauvegarde chiffrée",
                subtitle = "Archive protégée par mot de passe, à conserver hors du téléphone",
                onClick = {
                    exportLauncher.launch("dossier-medical-${java.time.LocalDate.now()}.zip")
                },
            )
            SettingRow(
                title = "Restaurer une sauvegarde",
                subtitle = "Remplace toutes les données actuelles (redémarrage ensuite)",
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            )
            SettingRow(
                title = "Supprimer toutes les données",
                subtitle = "Efface définitivement l'ensemble du dossier",
                destructive = true,
                onClick = { showWipe = true },
            )

            SettingsSection("Mise à jour")
            SettingRow(
                title = "Rechercher une mise à jour",
                subtitle = "Vérifie les nouvelles versions sur GitHub (requête anonyme, " +
                    "aucune donnée envoyée). Version actuelle : $currentVersion",
                onClick = {
                    updateChecking = true
                    updateUpToDate = false
                    updateError = null
                    vm.checkUpdate { info, error ->
                        updateChecking = false
                        when {
                            info != null && com.medicapp.updates.UpdateChecker.isNewer(
                                currentVersion, info.versionName
                            ) -> updateRelease = info
                            info != null -> updateUpToDate = true
                            else -> updateError =
                                "Impossible de joindre GitHub.\nDétail technique : $error"
                        }
                    }
                },
            )
            if (com.medicapp.updates.ApkInstaller.downloadedApk(context).exists()) {
                SettingRow(
                    title = "Installer l'APK téléchargé",
                    subtitle = "Lance l'installation de la mise à jour déjà présente sur le téléphone",
                    onClick = { com.medicapp.updates.ApkInstaller.install(context) },
                )
            }

            SettingsSection("Intelligence artificielle (bêta)")
            if (aiInstalled) {
                SettingRow(
                    title = "Moteur IA installé",
                    subtitle = "${com.medicapp.ai.GemmaEngine.MODEL_LABEL} — " +
                        "${aiEngine.installedSizeMb()} Mo · fonctionne hors ligne",
                    onClick = {},
                )
                SettingRow(
                    title = "Supprimer le modèle IA",
                    subtitle = "Libère environ 2 Go d'espace de stockage",
                    destructive = true,
                    onClick = { showDeleteAi = true },
                )
            } else {
                SettingRow(
                    title = "Télécharger le moteur IA (≈ 2 Go)",
                    subtitle = "Gemma 3n — transcription approfondie des documents : tampons, " +
                        "écriture difficile, mises en page complexes. Wi-Fi conseillé. " +
                        "Fonctionne hors ligne après téléchargement : aucune donnée " +
                        "n'est envoyée sur Internet.",
                    onClick = {
                        val target = aiEngine.modelFile()
                        target.parentFile?.mkdirs()
                        val request = DownloadManager.Request(Uri.parse(com.medicapp.ai.GemmaEngine.MODEL_URL))
                            .setTitle("Moteur IA — Dossier Médical")
                            .setDescription("Gemma 3n E2B (≈ 2 Go)")
                            .setDestinationUri(Uri.fromFile(target))
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setAllowedOverMetered(false)
                            .setAllowedOverRoaming(false)
                        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        aiDownloadId = manager.enqueue(request)
                        Toast.makeText(
                            context,
                            "Téléchargement du moteur IA démarré — suivez la notification système",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }

            SettingsSection("À propos")
            Text(
                "Politique de confidentialité — Cette application est un outil personnel : " +
                    "vos données de santé sont stockées uniquement sur votre téléphone, chiffrées " +
                    "(AES-256), et ne sont envoyées à aucun serveur tiers. Aucune analyse " +
                    "(analytics) ni publicité n'est intégrée. L'application ne fournit aucun avis " +
                    "médical. Vous pouvez exporter vos données à tout moment (portabilité) ou les " +
                    "supprimer définitivement ci-dessus.\n\nVersion 1.0.0",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showChangePin) {
        ChangePinDialog(vm, onDismiss = { showChangePin = false })
    }

    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text("Supprimer toutes les données ?") },
            text = {
                Text(
                    "L'intégralité du dossier médical (tous les profils, documents et rappels) " +
                        "sera définitivement effacée de ce téléphone. Action irréversible."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.wipeAllData(context)
                    showWipe = false
                    Toast.makeText(context, "Données supprimées", Toast.LENGTH_SHORT).show()
                }) { Text("Tout supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showWipe = false }) { Text("Annuler") } },
        )
    }

    showExportPassword?.let { uri ->
        PasswordDialog(
            title = "Mot de passe de la sauvegarde",
            confirmLabel = "Exporter",
            busy = busy,
            onDismiss = { showExportPassword = null },
            onConfirm = { password ->
                busy = true
                vm.exportBackup(context, uri, password) { ok ->
                    busy = false
                    showExportPassword = null
                    Toast.makeText(
                        context,
                        if (ok) "Sauvegarde exportée" else "Échec de l'export",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    showImportPassword?.let { uri ->
        PasswordDialog(
            title = "Mot de passe de la sauvegarde",
            confirmLabel = "Restaurer",
            busy = busy,
            onDismiss = { showImportPassword = null },
            onConfirm = { password ->
                busy = true
                vm.importBackup(context, uri, password) { ok ->
                    busy = false
                    showImportPassword = null
                    if (ok) {
                        Toast.makeText(context, "Restauration réussie — redémarrage…", Toast.LENGTH_SHORT).show()
                        restartApp()
                    } else {
                        Toast.makeText(context, "Mot de passe incorrect ou archive invalide", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    if (updateChecking) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Recherche de mise à jour") },
            text = { Text("Interrogation de GitHub…") },
            confirmButton = {},
        )
    }

    if (updateUpToDate) {
        AlertDialog(
            onDismissRequest = { updateUpToDate = false },
            title = { Text("Application à jour") },
            text = { Text("La version installée ($currentVersion) est la plus récente disponible.") },
            confirmButton = {
                TextButton(onClick = { updateUpToDate = false }) { Text("OK") }
            },
        )
    }

    updateError?.let { message ->
        AlertDialog(
            onDismissRequest = { updateError = null },
            title = { Text("Recherche impossible") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { updateError = null }) { Text("OK") } },
        )
    }

    updateRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("Version ${release.versionName} disponible") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        release.notes.ifBlank { "Nouvelle version disponible sur GitHub." },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Le téléchargement utilise le gestionnaire de téléchargement du téléphone ; " +
                            "l'installation peut demander d'autoriser les sources inconnues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = release.apkUrl != null,
                    onClick = {
                        val url = release.apkUrl ?: return@Button
                        downloadId = com.medicapp.updates.ApkInstaller.download(context, url)
                        Toast.makeText(
                            context,
                            "Téléchargement démarré — suivez la notification système",
                            Toast.LENGTH_LONG,
                        ).show()
                        updateRelease = null
                    },
                ) { Text("Télécharger") }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) { Text("Plus tard") }
            },
        )
    }

    if (showDeleteAi) {
        AlertDialog(
            onDismissRequest = { showDeleteAi = false },
            title = { Text("Supprimer le modèle IA ?") },
            text = { Text("Libère environ 2 Go. L'amélioration IA de la transcription redeviendra indisponible (l'OCR classique continue de fonctionner).") },
            confirmButton = {
                TextButton(onClick = {
                    aiEngine.deleteModel()
                    aiInstalled = false
                    showDeleteAi = false
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAi = false }) { Text("Annuler") } },
        )
    }

    if (updateReady) {
        AlertDialog(
            onDismissRequest = { updateReady = false },
            title = { Text("Téléchargement terminé") },
            text = { Text("La mise à jour a été téléchargée. Lancer l'installation maintenant ?") },
            confirmButton = {
                TextButton(onClick = {
                    updateReady = false
                    com.medicapp.updates.ApkInstaller.install(context)
                }) { Text("Installer") }
            },
            dismissButton = {
                TextButton(onClick = { updateReady = false }) { Text("Plus tard") }
            },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe (8 caractères minimum)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("Confirmer le mot de passe") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Attention : ce mot de passe est la seule façon de lire la sauvegarde. " +
                        "Il n'est pas récupérable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    error = null
                    when {
                        password.length < 8 -> error = "8 caractères minimum"
                        password != confirmation -> error = "Les mots de passe ne correspondent pas"
                        else -> onConfirm(password)
                    }
                },
            ) { Text(if (busy) "…" else confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChangePinDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var phase by remember { mutableStateOf(0) } // 0 = code actuel, 1 = nouveau, 2 = confirmation
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer le code PIN") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (phase) {
                        0 -> "Saisissez votre code actuel"
                        1 -> "Nouveau code (4 à 6 chiffres)"
                        else -> "Confirmez le nouveau code"
                    }
                )
                Spacer(Modifier.height(8.dp))
                val pin = when (phase) { 0 -> currentPin; 1 -> newPin; else -> confirmPin }
                PinDots(filled = pin.length, total = maxOf(4, pin.length))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(6)
                        when (phase) {
                            0 -> currentPin = digits
                            1 -> newPin = digits
                            else -> confirmPin = digits
                        }
                    },
                    label = { Text("Code") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = null
                when (phase) {
                    0 -> {
                        if (currentPin.length < 4) {
                            error = "Code trop court"
                        } else {
                            phase = 1
                        }
                    }
                    1 -> {
                        if (newPin.length in 4..6) phase = 2 else error = "4 à 6 chiffres requis"
                    }
                    else -> {
                        if (confirmPin == newPin) {
                            vm.changePin(currentPin, newPin) { ok ->
                                if (ok) onDismiss() else {
                                    phase = 0
                                    error = "Code actuel incorrect"
                                }
                            }
                        } else {
                            error = "Les codes ne correspondent pas"
                        }
                    }
                }
            }) { Text("Continuer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
