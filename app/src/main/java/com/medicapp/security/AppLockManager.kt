package com.medicapp.security

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * État de verrouillage applicatif en mémoire.
 * Verrouillage automatique après une durée d'inactivité (touche écran ou
 * retour au premier plan), y compris lorsque l'application est en arrière-plan.
 */
class AppLockManager(
    private val scope: CoroutineScope,
    private val autoLockMinutesProvider: () -> Int,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    @Volatile
    var lastUserActivityAt: Long = SystemClock.elapsedRealtime()
        private set

    fun noteUserInteraction() {
        lastUserActivityAt = SystemClock.elapsedRealtime()
    }

    fun lock() {
        _unlocked.value = false
    }

    fun unlock() {
        noteUserInteraction()
        _unlocked.value = true
    }

    fun startWatching() {
        scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (_unlocked.value) {
                    val limitMs = autoLockMinutesProvider() * 60_000L
                    if (SystemClock.elapsedRealtime() - lastUserActivityAt >= limitMs) {
                        lock()
                    }
                }
            }
        }
    }

    /** À l'appelant d'invoquer au retour au premier plan (verrouillage immédiat si délai dépassé). */
    fun checkShouldLockNow(): Boolean {
        if (!_unlocked.value) return false
        val limitMs = autoLockMinutesProvider() * 60_000L
        return SystemClock.elapsedRealtime() - lastUserActivityAt >= limitMs
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 10_000L
    }
}
