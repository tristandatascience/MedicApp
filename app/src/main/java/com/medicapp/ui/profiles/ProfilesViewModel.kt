package com.medicapp.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.Profile
import com.medicapp.data.prefs.AppSettings
import com.medicapp.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Profil actuellement sélectionné (bascule depuis l'accueil, § 4.9). */
class CurrentProfileViewModel(private val container: AppContainer) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = container.profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentProfile: StateFlow<Profile?> =
        combine(container.profileRepository.observeAll(), container.settings.settings) { list, s ->
            list.firstOrNull { it.id == s.currentProfileId } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Persiste le profil par défaut dès qu'il existe (première sélection implicite).
        viewModelScope.launch {
            container.profileRepository.observeAll().collect { list ->
                val s = container.settings.current()
                if (s.currentProfileId == 0L && list.isNotEmpty()) {
                    container.settings.setCurrentProfileId(list.first().id)
                }
            }
        }
    }

    fun switchTo(profileId: Long) {
        viewModelScope.launch { container.settings.setCurrentProfileId(profileId) }
    }
}

/** Gestion complète des profils (création, renommage, suppression). */
class ProfilesViewModel(private val container: AppContainer) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = container.profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentProfileId: StateFlow<Long> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        .let { settingsFlow ->
            combine(settingsFlow, container.profileRepository.observeAll()) { s, list ->
                (s?.currentProfileId ?: 0L).takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id ?: 0L
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
        }

    fun switchTo(profileId: Long) {
        viewModelScope.launch { container.settings.setCurrentProfileId(profileId) }
    }

    fun create(name: String, birthDate: java.time.LocalDate?, isSelf: Boolean, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = container.profileRepository.upsert(
                Profile(name = name.trim(), birthDate = birthDate, isSelf = isSelf)
            )
            onCreated(id)
        }
    }

    fun rename(profile: Profile, newName: String) {
        viewModelScope.launch {
            container.profileRepository.upsert(profile.copy(name = newName.trim()))
        }
    }

    /** Supprime le profil et toutes ses fiches (cascade) ; bascule si nécessaire. */
    fun delete(profile: Profile) {
        viewModelScope.launch {
            container.profileRepository.delete(profile.id)
            if (currentProfileId.value == profile.id) {
                profiles.value.firstOrNull { it.id != profile.id }?.let {
                    container.settings.setCurrentProfileId(it.id)
                }
            }
        }
    }
}
