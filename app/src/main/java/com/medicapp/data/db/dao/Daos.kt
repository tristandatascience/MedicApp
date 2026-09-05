package com.medicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.medicapp.data.db.entity.Appointment
import com.medicapp.data.db.entity.DocumentEntity
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.data.db.entity.Exam
import com.medicapp.data.db.entity.Prescription
import com.medicapp.data.db.entity.PrescriptionMedicine
import com.medicapp.data.db.entity.Profile
import com.medicapp.data.db.entity.Reminder
import com.medicapp.data.db.entity.ReminderKind
import com.medicapp.data.db.entity.Treatment
import com.medicapp.data.db.entity.Vaccination
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Profils
// ---------------------------------------------------------------------------

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isSelf DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<Profile?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------
// Vaccinations
// ---------------------------------------------------------------------------

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM vaccinations WHERE profileId = :profileId ORDER BY injectionDate DESC")
    fun observeForProfile(profileId: Long): Flow<List<Vaccination>>

    @Query("SELECT * FROM vaccinations WHERE id = :id")
    fun observeById(id: Long): Flow<Vaccination?>

    @Query("SELECT * FROM vaccinations WHERE id = :id")
    suspend fun getById(id: Long): Vaccination?

    @Query(
        """SELECT * FROM vaccinations WHERE profileId = :profileId
           AND nextDueDate IS NOT NULL AND nextDueDate BETWEEN :fromEpochDay AND :toEpochDay
           ORDER BY nextDueDate ASC"""
    )
    suspend fun boostersBetween(profileId: Long, fromEpochDay: Long, toEpochDay: Long): List<Vaccination>

    @Query(
        """SELECT * FROM vaccinations WHERE profileId = :profileId AND (
               vaccineName LIKE :q ESCAPE '\'
            OR disease LIKE :q ESCAPE '\'
            OR lotNumber LIKE :q ESCAPE '\'
            OR provider LIKE :q ESCAPE '\'
            OR notes LIKE :q ESCAPE '\')
           ORDER BY injectionDate DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<Vaccination>

    @Insert
    suspend fun insert(v: Vaccination): Long

    @Update
    suspend fun update(v: Vaccination)

    @Query("DELETE FROM vaccinations WHERE id = :id")
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------
// Traitements
// ---------------------------------------------------------------------------

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments WHERE profileId = :profileId ORDER BY startDate DESC")
    fun observeForProfile(profileId: Long): Flow<List<Treatment>>

    @Query(
        """SELECT * FROM treatments WHERE profileId = :profileId
           AND (endDate IS NULL OR endDate >= :todayEpochDay)
           ORDER BY startDate DESC"""
    )
    fun observeActive(profileId: Long, todayEpochDay: Long): Flow<List<Treatment>>

    @Query(
        """SELECT * FROM treatments WHERE profileId = :profileId AND endDate < :todayEpochDay
           ORDER BY endDate DESC"""
    )
    fun observeHistory(profileId: Long, todayEpochDay: Long): Flow<List<Treatment>>

    @Query("SELECT * FROM treatments WHERE id = :id")
    fun observeById(id: Long): Flow<Treatment?>

    @Query("SELECT * FROM treatments WHERE id = :id")
    suspend fun getById(id: Long): Treatment?

    @Query(
        """SELECT * FROM treatments WHERE profileId = :profileId AND (
               drugName LIKE :q ESCAPE '\'
            OR dosage LIKE :q ESCAPE '\'
            OR frequencyLabel LIKE :q ESCAPE '\'
            OR prescriber LIKE :q ESCAPE '\'
            OR notes LIKE :q ESCAPE '\')
           ORDER BY startDate DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<Treatment>

    @Insert
    suspend fun insert(t: Treatment): Long

    @Update
    suspend fun update(t: Treatment)

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------
// Ordonnances
// ---------------------------------------------------------------------------

data class PrescriptionWithMedicines(
    @Embedded val prescription: Prescription,
    @Relation(parentColumn = "id", entityColumn = "prescriptionId")
    val medicines: List<PrescriptionMedicine>,
)

@Dao
interface PrescriptionDao {
    @Transaction
    @Query("SELECT * FROM prescriptions WHERE profileId = :profileId ORDER BY prescriptionDate DESC")
    fun observeForProfile(profileId: Long): Flow<List<PrescriptionWithMedicines>>

    @Transaction
    @Query("SELECT * FROM prescriptions WHERE id = :id")
    fun observeById(id: Long): Flow<PrescriptionWithMedicines?>

    @Transaction
    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getById(id: Long): PrescriptionWithMedicines?

    @Query(
        """SELECT DISTINCT prescriptions.* FROM prescriptions
           LEFT JOIN prescription_medicines ON prescription_medicines.prescriptionId = prescriptions.id
           WHERE prescriptions.profileId = :profileId AND (
               prescriptions.prescriber LIKE :q ESCAPE '\'
            OR prescriptions.specialty LIKE :q ESCAPE '\'
            OR prescriptions.notes LIKE :q ESCAPE '\'
            OR prescription_medicines.name LIKE :q ESCAPE '\'
            OR prescription_medicines.dosage LIKE :q ESCAPE '\')
           ORDER BY prescriptions.prescriptionDate DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<Prescription>

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getRawById(id: Long): Prescription?

    @Insert
    suspend fun insert(p: Prescription): Long

    @Update
    suspend fun update(p: Prescription)

    @Query("DELETE FROM prescriptions WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertMedicine(m: PrescriptionMedicine): Long

    @Update
    suspend fun updateMedicine(m: PrescriptionMedicine)

    @Query("DELETE FROM prescription_medicines WHERE prescriptionId = :prescriptionId")
    suspend fun clearMedicines(prescriptionId: Long)
}

// ---------------------------------------------------------------------------
// Examens
// ---------------------------------------------------------------------------

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE profileId = :profileId ORDER BY examDate DESC")
    fun observeForProfile(profileId: Long): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    fun observeById(id: Long): Flow<Exam?>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): Exam?

    @Query(
        """SELECT * FROM exams WHERE profileId = :profileId AND (
               title LIKE :q ESCAPE '\'
            OR laboratory LIKE :q ESCAPE '\'
            OR prescriber LIKE :q ESCAPE '\'
            OR notes LIKE :q ESCAPE '\')
           ORDER BY examDate DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<Exam>

    @Insert
    suspend fun insert(e: Exam): Long

    @Update
    suspend fun update(e: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------
// Rendez-vous
// ---------------------------------------------------------------------------

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE profileId = :profileId AND dateTime >= :nowMillis ORDER BY dateTime ASC")
    fun observeUpcoming(profileId: Long, nowMillis: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE profileId = :profileId AND dateTime < :nowMillis ORDER BY dateTime DESC")
    fun observePast(profileId: Long, nowMillis: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE profileId = :profileId AND dateTime >= :nowMillis ORDER BY dateTime ASC LIMIT 1")
    fun observeNext(profileId: Long, nowMillis: Long): Flow<Appointment?>

    @Query("SELECT * FROM appointments WHERE id = :id")
    fun observeById(id: Long): Flow<Appointment?>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Long): Appointment?

    @Query(
        """SELECT * FROM appointments WHERE profileId = :profileId AND (
               professional LIKE :q ESCAPE '\'
            OR establishment LIKE :q ESCAPE '\'
            OR specialty LIKE :q ESCAPE '\'
            OR address LIKE :q ESCAPE '\'
            OR reason LIKE :q ESCAPE '\'
            OR notes LIKE :q ESCAPE '\')
           ORDER BY dateTime DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<Appointment>

    @Insert
    suspend fun insert(a: Appointment): Long

    @Update
    suspend fun update(a: Appointment)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------
// Documents numérisés
// ---------------------------------------------------------------------------

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE ownerType = :owner AND ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeForOwner(owner: DocumentOwner, ownerId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query(
        """SELECT * FROM documents WHERE profileId = :profileId AND (
               title LIKE :q ESCAPE '\' OR ocrText LIKE :q ESCAPE '\')
           ORDER BY createdAt DESC"""
    )
    suspend fun search(profileId: Long, q: String): List<DocumentEntity>

    @Insert
    suspend fun insert(d: DocumentEntity): Long

    @Update
    suspend fun update(d: DocumentEntity)

    @Query("UPDATE documents SET ocrText = :ocrText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOcr(id: Long, ocrText: String?, updatedAt: Long)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>
}

// ---------------------------------------------------------------------------
// Rappels
// ---------------------------------------------------------------------------

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE fired = 0 AND scheduled = 0 AND triggerAt >= :fromMillis")
    suspend fun toSchedule(fromMillis: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE fired = 0")
    fun observePending(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE kind = :kind AND referenceId = :referenceId AND fired = 0")
    suspend fun pendingByReference(kind: ReminderKind, referenceId: Long): List<Reminder>

    @Insert
    suspend fun insert(r: Reminder): Long

    @Update
    suspend fun update(r: Reminder)

    @Query("UPDATE reminders SET scheduled = :scheduled WHERE id = :id")
    suspend fun markScheduled(id: Long, scheduled: Boolean)

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders WHERE kind = :kind AND referenceId = :referenceId AND fired = 0")
    suspend fun deletePendingByReference(kind: ReminderKind, referenceId: Long)

    @Query("SELECT * FROM reminders WHERE fired = 0")
    suspend fun allPending(): List<Reminder>
}
