package com.medicapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medicapp.data.crypto.Pbkdf2PinHasher
import com.medicapp.data.storage.StorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Réglages applicatifs (aucune donnée de santé). */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val storageMode: StorageMode = StorageMode.LOCAL,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 3,
    val flagSecure: Boolean = true,
    val dynamicColors: Boolean = true,
    val currentProfileId: Long = 0L,
    /** Rappels vaccins : nombre de jours avant l'échéance (J-30, J-7, J-1). */
    val vaccineReminderDays: Set<Int> = setOf(30, 7, 1),
    /** Rappels RDV : décalage en minutes avant le rendez-vous (J-1 = 1440, H-2 = 120). */
    val appointmentReminderOffsetsMin: Set<Int> = setOf(24 * 60, 120),
    /** Longueur du code PIN (4 à 6 chiffres). */
    val pinLength: Int = 4,
)

class SettingsRepository(private val context: Context) {

    private val pinHasher = Pbkdf2PinHasher()

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val FLAG_SECURE = booleanPreferencesKey("flag_secure")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
        val VACCINE_REMINDER_DAYS = stringSetPreferencesKey("vaccine_reminder_days")
        val APPOINTMENT_REMINDER_OFFSETS = stringSetPreferencesKey("appointment_reminder_offsets_min")
        val PIN_LENGTH = intPreferencesKey("pin_length")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            storageMode = StorageMode.fromName(p[Keys.STORAGE_MODE]),
            biometricEnabled = p[Keys.BIOMETRIC_ENABLED] ?: false,
            autoLockMinutes = (p[Keys.AUTO_LOCK_MINUTES] ?: 3).coerceIn(1, 10),
            flagSecure = p[Keys.FLAG_SECURE] ?: true,
            dynamicColors = p[Keys.DYNAMIC_COLORS] ?: true,
            currentProfileId = p[Keys.CURRENT_PROFILE_ID] ?: 0L,
            vaccineReminderDays = (p[Keys.VACCINE_REMINDER_DAYS])
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(30, 7, 1),
            appointmentReminderOffsetsMin = (p[Keys.APPOINTMENT_REMINDER_OFFSETS])
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(24 * 60, 120),
            pinLength = (p[Keys.PIN_LENGTH] ?: 4).coerceIn(4, 6),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    // ---- Mutateurs ----

    suspend fun setOnboardingDone() =
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = true }

    suspend fun setStorageMode(mode: StorageMode) =
        context.settingsDataStore.edit { it[Keys.STORAGE_MODE] = mode.name }

    suspend fun setBiometricEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }

    suspend fun setAutoLockMinutes(minutes: Int) =
        context.settingsDataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes.coerceIn(1, 10) }

    suspend fun setFlagSecure(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.FLAG_SECURE] = enabled }

    suspend fun setDynamicColors(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }

    suspend fun setCurrentProfileId(id: Long) =
        context.settingsDataStore.edit { it[Keys.CURRENT_PROFILE_ID] = id }

    suspend fun setVaccineReminderDays(days: Set<Int>) =
        context.settingsDataStore.edit {
            it[Keys.VACCINE_REMINDER_DAYS] = days.map { v -> v.toString() }.toSet()
        }

    suspend fun setAppointmentReminderOffsets(minutes: Set<Int>) =
        context.settingsDataStore.edit {
            it[Keys.APPOINTMENT_REMINDER_OFFSETS] = minutes.map { v -> v.toString() }.toSet()
        }

    // ---- PIN ----

    suspend fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN invalide" }
        val salt = pinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { pinHasher.hash(pin, salt) }
        context.settingsDataStore.edit {
            it[Keys.PIN_SALT] = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
            it[Keys.PIN_HASH] = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
            it[Keys.PIN_LENGTH] = pin.length
        }
    }

    suspend fun hasPin(): Boolean = context.settingsDataStore.data.first().let { p ->
        p[Keys.PIN_SALT] != null && p[Keys.PIN_HASH] != null
    }

    /** Efface toutes les préférences (retour à l'état de première installation). */
    suspend fun wipe() {
        context.settingsDataStore.edit { it.clear() }
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val p = context.settingsDataStore.data.first()
        val saltB64 = p[Keys.PIN_SALT] ?: return@withContext false
        val hashB64 = p[Keys.PIN_HASH] ?: return@withContext false
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val expected = android.util.Base64.decode(hashB64, android.util.Base64.NO_WRAP)
        pinHasher.verify(pin, salt, expected)
    }
}
