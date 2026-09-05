package com.medicapp.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.prefs.SettingsRepository
import com.medicapp.security.AppLockManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LockViewModel(
    private val settings: SettingsRepository,
    private val appLock: AppLockManager,
) : ViewModel() {

    private val _entered = MutableStateFlow("")
    val entered: StateFlow<String> = _entered

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error

    private val _pinLength = MutableStateFlow(4)
    val pinLength: StateFlow<Int> = _pinLength

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled

    val unlocked: StateFlow<Boolean> = appLock.unlocked

    init {
        viewModelScope.launch {
            settings.settings.collect {
                _pinLength.value = it.pinLength
                _biometricEnabled.value = it.biometricEnabled
            }
        }
    }

    fun onDigit(digit: Char) {
        if (_error.value) return
        val current = _entered.value
        if (current.length >= _pinLength.value) return
        val next = current + digit
        _entered.value = next
        if (next.length == _pinLength.value) verify(next)
    }

    fun onBackspace() {
        _entered.value = _entered.value.dropLast(1)
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            val ok = settings.verifyPin(pin)
            if (ok) {
                appLock.unlock()
                _entered.value = ""
            } else {
                _error.value = true
                delay(ERROR_DISPLAY_MS)
                _entered.value = ""
                _error.value = false
            }
        }
    }

    fun onBiometricSuccess() = appLock.unlock()

    companion object {
        private const val ERROR_DISPLAY_MS = 600L
    }
}
