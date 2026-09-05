package com.medicapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.medicapp.notifications.NotificationsCenter
import com.medicapp.notifications.ReminderScheduler
import com.medicapp.ui.AppRoot
import com.medicapp.ui.LocalAppContainer
import com.medicapp.ui.theme.MedicTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Activité unique. Étend FragmentActivity pour le BiometricPrompt.
 * FLAG_SECURE activé par défaut (anti-capture d'écran, désactivable en réglages).
 */
class MainActivity : FragmentActivity() {

    private val container by lazy { (application as MedicApplication).container }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            container.settings.settings
                .map { it.flagSecure }
                .distinctUntilChanged()
                .collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                MedicTheme {
                    AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (container.appLock.checkShouldLockNow()) container.appLock.lock()
        container.appLock.noteUserInteraction()
        // Re-planification des rappels (filet de sécurité après redémarrage du processus).
        NotificationsCenter.ensureChannels(this)
        lifecycleScope.launch {
            runCatching { ReminderScheduler.syncAll(this@MainActivity, container) }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        container.appLock.noteUserInteraction()
        return super.dispatchTouchEvent(ev)
    }
}
