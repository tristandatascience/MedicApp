package com.medicapp.data.repo

import com.medicapp.data.db.dao.AppointmentDao
import com.medicapp.data.db.dao.DocumentDao
import com.medicapp.data.db.dao.ExamDao
import com.medicapp.data.db.dao.PrescriptionDao
import com.medicapp.data.db.dao.TreatmentDao
import com.medicapp.data.db.dao.VaccinationDao
import com.medicapp.data.db.entity.DocumentOwner
import java.time.LocalDate

enum class SearchDomain { VACCINATION, TREATMENT, PRESCRIPTION, EXAM, APPOINTMENT, DOCUMENT }

data class SearchHit(
    val domain: SearchDomain,
    val id: Long,
    val profileId: Long,
    val title: String,
    val subtitle: String?,
    val date: LocalDate?,
)

/**
 * Recherche globale : titres, notes et textes OCR de tous les modules
 * (Cf. cahier des charges § 4.8 — aucune interprétation des valeurs).
 */
class SearchRepository(
    private val vaccinationDao: VaccinationDao,
    private val treatmentDao: TreatmentDao,
    private val prescriptionDao: PrescriptionDao,
    private val examDao: ExamDao,
    private val appointmentDao: AppointmentDao,
    private val documentDao: DocumentDao,
) {
    suspend fun search(profileId: Long, rawQuery: String): List<SearchHit> {
        val q = escapeLike(rawQuery.trim())
        if (q.isEmpty()) return emptyList()

        val hits = mutableListOf<SearchHit>()
        hits += vaccinationDao.search(profileId, q).map {
            SearchHit(SearchDomain.VACCINATION, it.id, it.profileId, it.vaccineName, it.disease, it.injectionDate)
        }
        hits += treatmentDao.search(profileId, q).map {
            SearchHit(SearchDomain.TREATMENT, it.id, it.profileId, it.drugName, it.dosage, it.startDate)
        }
        hits += prescriptionDao.search(profileId, q).map {
            SearchHit(SearchDomain.PRESCRIPTION, it.id, it.profileId, "Ordonnance — ${it.prescriber ?: "prescripteur inconnu"}", it.specialty, it.prescriptionDate)
        }
        hits += examDao.search(profileId, q).map {
            SearchHit(SearchDomain.EXAM, it.id, it.profileId, it.title ?: categoryLabel(it.category), it.laboratory, it.examDate)
        }
        hits += appointmentDao.search(profileId, q).map {
            SearchHit(SearchDomain.APPOINTMENT, it.id, it.profileId, it.professional ?: it.establishment ?: "Rendez-vous", it.reason, it.dateTime.toLocalDate())
        }
        hits += documentDao.search(profileId, q).map {
            SearchHit(SearchDomain.DOCUMENT, it.id, it.profileId, it.title, ownerLabel(it.ownerType), it.createdAt.toLocalDate())
        }
        return hits.distinctBy { it.domain to it.id }
            .sortedByDescending { it.date ?: LocalDate.MIN }
    }

    companion object {
        /** Échappe les jokers SQL et enveloppe le motif pour un « contient ». */
        fun escapeLike(input: String): String {
            val escaped = input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }

        fun categoryLabel(category: com.medicapp.data.db.entity.ExamCategory): String = when (category) {
            com.medicapp.data.db.entity.ExamCategory.BLOOD_TEST -> "Analyse sanguine"
            com.medicapp.data.db.entity.ExamCategory.RADIOLOGY -> "Radiologie"
            com.medicapp.data.db.entity.ExamCategory.ULTRASOUND -> "Échographie"
            com.medicapp.data.db.entity.ExamCategory.MRI -> "IRM"
            com.medicapp.data.db.entity.ExamCategory.OTHER -> "Autre examen"
        }

        fun ownerLabel(owner: DocumentOwner): String = when (owner) {
            DocumentOwner.VACCINATION -> "Document — vaccination"
            DocumentOwner.TREATMENT -> "Document — traitement"
            DocumentOwner.PRESCRIPTION -> "Document — ordonnance"
            DocumentOwner.EXAM -> "Document — examen"
            DocumentOwner.APPOINTMENT -> "Document — rendez-vous"
            DocumentOwner.STANDALONE -> "Document"
        }
    }
}
