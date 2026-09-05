package com.medicapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.medicapp.ui.lock.LockScreen
import com.medicapp.ui.navigation.MedicNavHost
import com.medicapp.ui.onboarding.OnboardingScreen

/**
 * Point d'entrée : premier lancement -> assistant de configuration ;
 * sinon écran de verrouillage puis navigation principale.
 */
@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = null)
    val unlocked by container.appLock.unlocked.collectAsState()

    val loaded = settings
    when {
        loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !loaded.onboardingDone -> OnboardingScreen()
        !unlocked -> LockScreen()
        else -> MedicNavHost()
    }
}
