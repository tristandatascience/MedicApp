package com.medicapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.storage.StorageMode
import com.medicapp.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class OnboardingStep { WELCOME, STORAGE, PIN_LENGTH, PIN_ENTER, PIN_CONFIRM, BIOMETRIC, PROFILE }

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _step = MutableStateFlow(OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> = _step

    private val _entered = MutableStateFlow("")
    val entered: StateFlow<String> = _entered

    private val _pinLength = MutableStateFlow(4)
    val pinLength: StateFlow<Int> = _pinLength

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var firstPin: String = ""
        private set

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName

    private val _profileBirthDate = MutableStateFlow<LocalDate?>(null)
    val profileBirthDate: StateFlow<LocalDate?> = _profileBirthDate

    private val _profileIsSelf = MutableStateFlow(true)
    val profileIsSelf: StateFlow<Boolean> = _profileIsSelf

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating

    fun next() {
        _error.value = null
        when (_step.value) {
            OnboardingStep.WELCOME -> _step.value = OnboardingStep.STORAGE
            else -> Unit
        }
    }

    fun chooseStorage(mode: StorageMode) {
        viewModelScope.launch {
            container.settings.setStorageMode(mode)
            _step.value = OnboardingStep.PIN_LENGTH
        }
    }

    fun choosePinLength(length: Int) {
        _pinLength.value = length
        _step.value = OnboardingStep.PIN_ENTER
    }

    fun onDigit(digit: Char) {
        if (_entered.value.length >= _pinLength.value) return
        _entered.value += digit
        if (_entered.value.length == _pinLength.value) onPinComplete()
    }

    fun onBackspace() {
        _entered.value = _entered.value.dropLast(1)
    }

    private fun onPinComplete() {
        when (_step.value) {
            OnboardingStep.PIN_ENTER -> {
                firstPin = _entered.value
                _entered.value = ""
                _step.value = OnboardingStep.PIN_CONFIRM
            }
            OnboardingStep.PIN_CONFIRM -> {
                if (_entered.value == firstPin) {
                    val pin = _entered.value
                    _entered.value = ""
                    viewModelScope.launch {
                        container.settings.setPin(pin)
                        _step.value = OnboardingStep.BIOMETRIC
                    }
                } else {
                    _error.value = "Les codes ne correspondent pas, recommencez."
                    _entered.value = ""
                    _step.value = OnboardingStep.PIN_ENTER
                }
            }
            else -> Unit
        }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setBiometricEnabled(enabled)
            _step.value = OnboardingStep.PROFILE
        }
    }

    fun setProfileName(value: String) {
        _profileName.value = value
    }

    fun setProfileBirthDate(value: LocalDate?) {
        _profileBirthDate.value = value
    }

    fun setProfileIsSelf(value: Boolean) {
        _profileIsSelf.value = value
    }

    fun finish(onDone: () -> Unit) {
        val name = _profileName.value.trim()
        if (name.isEmpty()) {
            _error.value = "Le nom du profil est obligatoire."
            return
        }
        _creating.value = true
        viewModelScope.launch {
            val id = container.profileRepository.upsert(
                com.medicapp.data.db.entity.Profile(
                    name = name,
                    birthDate = _profileBirthDate.value,
                    isSelf = _profileIsSelf.value,
                )
            )
            container.settings.setCurrentProfileId(id)
            container.settings.setOnboardingDone()
            container.appLock.unlock()
            _creating.value = false
            onDone()
        }
    }

    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
