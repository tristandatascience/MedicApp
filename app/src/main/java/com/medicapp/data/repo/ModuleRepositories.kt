package com.medicapp.data.repo

import androidx.room.withTransaction
import com.medicapp.data.db.MedicDatabase
import com.medicapp.data.db.dao.AppointmentDao
import com.medicapp.data.db.dao.ExamDao
import com.medicapp.data.db.dao.PrescriptionDao
import com.medicapp.data.db.dao.ProfileDao
import com.medicapp.data.db.dao.TreatmentDao
import com.medicapp.data.db.dao.VaccinationDao
import com.medicapp.data.db.dao.PrescriptionWithMedicines
import com.medicapp.data.db.entity.Appointment
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.data.db.entity.Exam
import com.medicapp.data.db.entity.Prescription
import com.medicapp.data.db.entity.PrescriptionMedicine
import com.medicapp.data.db.entity.Profile
import com.medicapp.data.db.entity.Treatment
import com.medicapp.data.db.entity.Vaccination
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Profils
// ---------------------------------------------------------------------------

class ProfileRepository(private val dao: ProfileDao) {
    fun observeAll(): Flow<List<Profile>> = dao.observeAll()

    fun observeById(id: Long): Flow<Profile?> = dao.observeById(id)

    suspend fun getById(id: Long): Profile? = dao.getById(id)

    suspend fun count(): Int = dao.count()

    suspend fun upsert(profile: Profile): Long =
        if (profile.id == 0L) dao.insert(profile) else {
            dao.update(profile); profile.id
        }

    /** Supprime un profil (les fiches liées sont supprimées en cascade). */
    suspend fun delete(id: Long) = dao.delete(id)
}

// ---------------------------------------------------------------------------
// Vaccinations
// ---------------------------------------------------------------------------

class VaccinationRepository(
    private val dao: VaccinationDao,
    private val documents: DocumentRepository,
    private val reminderPlanner: ReminderPlanner,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeForProfile(profileId: Long): Flow<List<Vaccination>> = dao.observeForProfile(profileId)

    fun observeById(id: Long): Flow<Vaccination?> = dao.observeById(id)

    suspend fun getById(id: Long): Vaccination? = dao.getById(id)

    suspend fun search(profileId: Long, q: String): List<Vaccination> =
        dao.search(profileId, SearchRepository.escapeLike(q))

    suspend fun upcomingBoosters(profileId: Long, daysAhead: Int): List<Vaccination> {
        val today = LocalDate.now(clock)
        return dao.boostersBetween(profileId, today.toEpochDay(), today.plusDays(daysAhead.toLong()).toEpochDay())
    }

    suspend fun upsert(v: Vaccination): Long {
        val id = if (v.id == 0L) dao.insert(v) else {
            dao.update(v); v.id
        }
        reminderPlanner.regenerateVaccineBoosterReminders(v.profileId, id, v.vaccineName, v.nextDueDate)
        return id
    }

    suspend fun delete(id: Long) {
        dao.getById(id)?.let {
            documents.deleteForOwner(DocumentOwner.VACCINATION, id)
            reminderPlanner.deletePending(com.medicapp.data.db.entity.ReminderKind.VACCINE_BOOSTER, id)
            dao.delete(id)
        }
    }
}

// ---------------------------------------------------------------------------
// Traitements
// ---------------------------------------------------------------------------

class TreatmentRepository(
    private val dao: TreatmentDao,
    private val documents: DocumentRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeForProfile(profileId: Long): Flow<List<Treatment>> = dao.observeForProfile(profileId)

    fun observeActive(profileId: Long): Flow<List<Treatment>> =
        dao.observeActive(profileId, LocalDate.now(clock).toEpochDay())

    fun observeHistory(profileId: Long): Flow<List<Treatment>> =
        dao.observeHistory(profileId, LocalDate.now(clock).toEpochDay())

    fun observeById(id: Long): Flow<Treatment?> = dao.observeById(id)

    suspend fun getById(id: Long): Treatment? = dao.getById(id)

    suspend fun search(profileId: Long, q: String): List<Treatment> =
        dao.search(profileId, SearchRepository.escapeLike(q))

    suspend fun upsert(t: Treatment): Long =
        if (t.id == 0L) dao.insert(t) else {
            dao.update(t); t.id
        }

    suspend fun delete(id: Long) {
        documents.deleteForOwner(DocumentOwner.TREATMENT, id)
        dao.delete(id)
    }
}

// ---------------------------------------------------------------------------
// Ordonnances
// ---------------------------------------------------------------------------

class PrescriptionRepository(
    private val db: MedicDatabase,
    private val documents: DocumentRepository,
) {
    private val dao: PrescriptionDao = db.prescriptionDao()

    fun observeForProfile(profileId: Long): Flow<List<PrescriptionWithMedicines>> =
        dao.observeForProfile(profileId)

    fun observeById(id: Long): Flow<PrescriptionWithMedicines?> = dao.observeById(id)

    suspend fun getById(id: Long): PrescriptionWithMedicines? = dao.getById(id)

    suspend fun search(profileId: Long, q: String): List<Prescription> =
        dao.search(profileId, SearchRepository.escapeLike(q))

    /** Enregistre l'ordonnance et sa liste de médicaments de façon atomique. */
    suspend fun save(prescription: Prescription, medicines: List<PrescriptionMedicine>): Long =
        db.withTransaction {
            val expiry = prescription.prescriptionDate
                .plusDays((prescription.validityDays ?: DEFAULT_VALIDITY_DAYS).toLong())
            val withExpiry = prescription.copy(expiryDate = expiry)
            val id = if (withExpiry.id == 0L) dao.insert(withExpiry) else {
                dao.update(withExpiry); withExpiry.id
            }
            dao.clearMedicines(id)
            medicines.sortedBy { it.position }.forEachIndexed { index, m ->
                dao.insertMedicine(m.copy(prescriptionId = id, position = index))
            }
            id
        }

    suspend fun delete(id: Long) {
        documents.deleteForOwner(DocumentOwner.PRESCRIPTION, id)
        dao.delete(id)
    }

    companion object {
        /** Durée de validité par défaut d'une ordonnance en France (12 mois). */
        const val DEFAULT_VALIDITY_DAYS = 365
    }
}

// ---------------------------------------------------------------------------
// Examens
// ---------------------------------------------------------------------------

class ExamRepository(
    private val dao: ExamDao,
    private val documents: DocumentRepository,
) {
    fun observeForProfile(profileId: Long): Flow<List<Exam>> = dao.observeForProfile(profileId)

    fun observeById(id: Long): Flow<Exam?> = dao.observeById(id)

    suspend fun getById(id: Long): Exam? = dao.getById(id)

    suspend fun search(profileId: Long, q: String): List<Exam> =
        dao.search(profileId, SearchRepository.escapeLike(q))

    suspend fun upsert(e: Exam): Long =
        if (e.id == 0L) dao.insert(e) else {
            dao.update(e); e.id
        }

    suspend fun delete(id: Long) {
        documents.deleteForOwner(DocumentOwner.EXAM, id)
        dao.delete(id)
    }
}

// ---------------------------------------------------------------------------
// Rendez-vous
// ---------------------------------------------------------------------------

class AppointmentRepository(
    private val dao: AppointmentDao,
    private val documents: DocumentRepository,
    private val reminderPlanner: ReminderPlanner,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeUpcoming(profileId: Long): Flow<List<Appointment>> =
        dao.observeUpcoming(profileId, System.currentTimeMillis())

    fun observePast(profileId: Long): Flow<List<Appointment>> =
        dao.observePast(profileId, System.currentTimeMillis())

    fun observeNext(profileId: Long): Flow<Appointment?> =
        dao.observeNext(profileId, System.currentTimeMillis())

    fun observeById(id: Long): Flow<Appointment?> = dao.observeById(id)

    suspend fun getById(id: Long): Appointment? = dao.getById(id)

    suspend fun search(profileId: Long, q: String): List<Appointment> =
        dao.search(profileId, SearchRepository.escapeLike(q))

    suspend fun upsert(a: Appointment): Long {
        val id = if (a.id == 0L) dao.insert(a) else {
            dao.update(a); a.id
        }
        val label = buildString {
            append(a.professional ?: a.establishment ?: "rendez-vous médical")
            a.specialty?.let { append(" (").append(it).append(")") }
        }
        reminderPlanner.regenerateAppointmentReminders(a.profileId, id, label, a.dateTime)
        return id
    }

    suspend fun delete(id: Long) {
        documents.deleteForOwner(DocumentOwner.APPOINTMENT, id)
        reminderPlanner.deletePending(com.medicapp.data.db.entity.ReminderKind.APPOINTMENT, id)
        dao.delete(id)
    }
}
