package com.medicapp.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.ui.LocalAppContainer
import com.medicapp.ui.common.PinDots
import com.medicapp.ui.common.PinPad

/** Écran de déverrouillage : code PIN, ou biométrie si activée. */
@Composable
fun LockScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm: LockViewModel = viewModel(
        factory = viewModelFactory { initializer { LockViewModel(container.settings, container.appLock) } }
    )
    val entered by vm.entered.collectAsState()
    val error by vm.error.collectAsState()
    val pinLength by vm.pinLength.collectAsState()
    val biometricEnabled by vm.biometricEnabled.collectAsState()

    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val canBiometric = remember(activity) {
        activity != null && BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val showBiometric: () -> Unit = {
        activity?.let { act ->
            val prompt = BiometricPrompt(
                act,
                ContextCompat.getMainExecutor(act),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        vm.onBiometricSuccess()
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller le dossier médical")
                .setSubtitle("Utilisez votre empreinte ou votre visage")
                .setNegativeButtonText("Utiliser le code PIN")
                .build()
            prompt.authenticate(info)
        }
    }

    // Proposition automatique de la biométrie à l'arrivée sur l'écran.
    LaunchedEffect(biometricEnabled, canBiometric) {
        if (biometricEnabled && canBiometric) showBiometric()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Dossier Médical", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            if (error) "Code incorrect" else "Saisissez votre code PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PinDots(filled = entered.length, total = pinLength)
        Spacer(Modifier.height(32.dp))
        PinPad(onDigit = vm::onDigit, onBackspace = vm::onBackspace, enabled = !error)
        Spacer(Modifier.height(24.dp))
        if (biometricEnabled && canBiometric) {
            OutlinedButton(onClick = showBiometric, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Déverrouiller avec la biométrie")
            }
        }
    }
}
