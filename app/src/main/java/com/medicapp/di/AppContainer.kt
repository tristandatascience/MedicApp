package com.medicapp.di

import android.content.Context
import com.medicapp.data.crypto.AndroidKeystoreWrapper
import com.medicapp.data.crypto.FileCipher
import com.medicapp.data.crypto.MasterKeyManager
import com.medicapp.data.crypto.Pbkdf2PinHasher
import com.medicapp.data.db.DatabaseFactory
import com.medicapp.data.db.MedicDatabase
import com.medicapp.data.prefs.SettingsRepository
import com.medicapp.data.repo.AppointmentRepository
import com.medicapp.data.repo.DashboardRepository
import com.medicapp.data.repo.DocumentRepository
import com.medicapp.data.repo.ExamRepository
import com.medicapp.data.repo.PrescriptionRepository
import com.medicapp.data.repo.ProfileRepository
import com.medicapp.data.repo.ReminderPlanner
import com.medicapp.data.repo.SearchRepository
import com.medicapp.data.repo.TreatmentRepository
import com.medicapp.data.repo.VaccinationRepository
import com.medicapp.data.storage.LocalDocumentStore
import com.medicapp.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Injection manuelle : tout est construit paresseusement depuis le contexte
 * d'application. La base n'est ouverte qu'au premier accès réel.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val pinHasher: Pbkdf2PinHasher by lazy { Pbkdf2PinHasher() }

    private val keystoreWrapper: com.medicapp.data.crypto.KeystoreWrapper by lazy { AndroidKeystoreWrapper() }

    val masterKeyManager: MasterKeyManager by lazy {
        MasterKeyManager(keystoreWrapper, File(appContext.filesDir, MASTER_KEY_FILE))
    }

    val fileCipher: FileCipher by lazy { FileCipher { masterKeyManager.masterKey() } }

    val database: MedicDatabase
        get() = databaseInstance ?: DatabaseFactory.create(appContext, masterKeyManager)
            .also { databaseInstance = it }

    @Volatile
    private var databaseInstance: MedicDatabase? = null

    /** Ferme la base courante : la prochaine accès en recrée une instance. */
    @Synchronized
    fun resetDatabase() {
        databaseInstance?.close()
        databaseInstance = null
    }

    /** Installe une clé maître (restauration de sauvegarde). */
    fun installMasterKey(keyBytes: ByteArray) {
        masterKeyManager.installKey(keyBytes)
    }

    private val documentStore: LocalDocumentStore by lazy { LocalDocumentStore(appContext.filesDir, fileCipher) }

    val documentRepository: DocumentRepository by lazy { DocumentRepository(database.documentDao(), documentStore) }

    private val reminderPlanner: ReminderPlanner by lazy { ReminderPlanner(database.reminderDao(), settings) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }

    val vaccinationRepository: VaccinationRepository by lazy {
        VaccinationRepository(database.vaccinationDao(), documentRepository, reminderPlanner)
    }

    val treatmentRepository: TreatmentRepository by lazy {
        TreatmentRepository(database.treatmentDao(), documentRepository)
    }

    val prescriptionRepository: PrescriptionRepository by lazy {
        PrescriptionRepository(database, documentRepository)
    }

    val examRepository: ExamRepository by lazy { ExamRepository(database.examDao(), documentRepository) }

    val appointmentRepository: AppointmentRepository by lazy {
        AppointmentRepository(database.appointmentDao(), documentRepository, reminderPlanner)
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(
            database.vaccinationDao(),
            database.treatmentDao(),
            database.prescriptionDao(),
            database.examDao(),
            database.appointmentDao(),
            database.documentDao(),
        )
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(database.appointmentDao(), database.treatmentDao(), database.vaccinationDao())
    }

    private val autoLockMinutesCache = AtomicInteger(3)

    val appLock: AppLockManager by lazy {
        applicationScope.launch {
            settings.settings.collect { autoLockMinutesCache.set(it.autoLockMinutes) }
        }
        AppLockManager(applicationScope) { autoLockMinutesCache.get() }
    }

    companion object {
        private const val MASTER_KEY_FILE = ".mk"
    }
}
