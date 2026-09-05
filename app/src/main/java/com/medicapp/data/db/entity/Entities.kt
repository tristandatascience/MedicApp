package com.medicapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

// ---------------------------------------------------------------------------
// Profils (gestion multi-membres : soi-même, enfants, proches)
// ---------------------------------------------------------------------------

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthDate: LocalDate? = null,
    val isSelf: Boolean = false,
    val colorArgb: Long = 0xFF006A60,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Carnet de vaccination
// ---------------------------------------------------------------------------

@Entity(
    tableName = "vaccinations",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("nextDueDate")],
)
data class Vaccination(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val vaccineName: String,
    val disease: String,
    val injectionDate: LocalDate,
    val lotNumber: String? = null,
    val provider: String? = null,
    val nextDueDate: LocalDate? = null,
    val documentId: Long? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Traitements
// ---------------------------------------------------------------------------

@Entity(
    tableName = "treatments",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("endDate")],
)
data class Treatment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val drugName: String,
    val dosage: String? = null,
    /** Heures de prise au format "HH:mm" (ex. ["08:00", "20:00"]). */
    val intakeTimes: List<String> = emptyList(),
    /** Libellé libre de fréquence (ex. "2 fois par jour", "1 comprimé le matin"). */
    val frequencyLabel: String? = null,
    val startDate: LocalDate,
    /** null si traitement continu. */
    val endDate: LocalDate? = null,
    val prescriber: String? = null,
    val prescriptionId: Long? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun isActive(today: LocalDate): Boolean = endDate == null || !endDate.isBefore(today)
}

// ---------------------------------------------------------------------------
// Ordonnances
// ---------------------------------------------------------------------------

@Entity(
    tableName = "prescriptions",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("expiryDate")],
)
data class Prescription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val prescriptionDate: LocalDate,
    val prescriber: String? = null,
    val specialty: String? = null,
    /** Durée de validité en jours (null : durée par défaut de 12 mois appliquée à l'enregistrement). */
    val validityDays: Int? = null,
    /** Date d'expiration calculée à l'enregistrement (date + validité). */
    val expiryDate: LocalDate? = null,
    val documentId: Long? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Entity(
    tableName = "prescription_medicines",
    foreignKeys = [
        ForeignKey(
            entity = Prescription::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("prescriptionId")],
)
data class PrescriptionMedicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prescriptionId: Long,
    val name: String,
    val dosage: String? = null,
    val duration: String? = null,
    val position: Int = 0,
)

// ---------------------------------------------------------------------------
// Résultats d'examens
// ---------------------------------------------------------------------------

/** Catégories d'examen — l'OCR n'interprète jamais les valeurs. */
enum class ExamCategory {
    BLOOD_TEST,
    RADIOLOGY,
    ULTRASOUND,
    MRI,
    OTHER,
}

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("examDate")],
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String? = null,
    val category: ExamCategory,
    val examDate: LocalDate,
    val laboratory: String? = null,
    val prescriber: String? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Rendez-vous
// ---------------------------------------------------------------------------

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("dateTime")],
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dateTime: LocalDateTime,
    val professional: String? = null,
    val establishment: String? = null,
    val specialty: String? = null,
    val address: String? = null,
    val reason: String? = null,
    /** Libellés des documents à apporter (cases cochées). */
    val documentsToBring: List<String> = emptyList(),
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Documents numérisés (photo/PDF multi-pages + texte OCR indexé)
// ---------------------------------------------------------------------------

enum class DocumentOwner { VACCINATION, TREATMENT, PRESCRIPTION, EXAM, APPOINTMENT, STANDALONE }

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("ownerType", "ownerId")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    /** "image/jpeg", "image/png" ou "application/pdf". */
    val mimeType: String,
    val pageCount: Int = 1,
    /** Identifiant du fichier chiffré dans le DocumentStore. */
    val storageKey: String,
    /** Texte OCR concaténé (indexé pour la recherche plein texte). */
    val ocrText: String? = null,
    val ownerType: DocumentOwner,
    val ownerId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Rappels planifiés (notifications locales)
// ---------------------------------------------------------------------------

enum class ReminderKind { TREATMENT_INTAKE, VACCINE_BOOSTER, APPOINTMENT }

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("kind", "referenceId"), Index("triggerAt")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val kind: ReminderKind,
    /** Identifiant de la fiche concernée (traitement, vaccination ou rendez-vous). */
    val referenceId: Long,
    val label: String,
    val triggerAt: LocalDateTime,
    val scheduled: Boolean = false,
    val fired: Boolean = false,
)
