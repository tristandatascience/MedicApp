package com.medicapp.ocr

import com.medicapp.data.db.entity.ExamCategory
import java.time.LocalDate
import java.time.LocalTime

/**
 * Extraction heuristique de champs à partir du texte OCR pour pré-remplir les
 * fiches (§ 4.2, § 4.3). Les résultats ne sont jamais enregistrés sans
 * validation de l'utilisateur.
 */
object OcrFieldParser {

    data class ParsedFields(
        val dates: List<LocalDate> = emptyList(),
        val times: List<LocalTime> = emptyList(),
        /** Paires (nom du médicament, dosage détecté). */
        val drugs: List<Pair<String, String?>> = emptyList(),
        val prescriber: String? = null,
        val laboratory: String? = null,
        val vaccineName: String? = null,
        val disease: String? = null,
        val examCategory: ExamCategory? = null,
    ) {
        /**
         * Date la plus plausible pour une fiche : la plus récente des dates
         * passées si possible, sinon la première date trouvée.
         */
        val mostLikelyDate: LocalDate?
            get() {
                val today = LocalDate.now()
                return dates.filter { !it.isAfter(today) }.maxOrNull() ?: dates.firstOrNull()
            }
    }

    private val MONTHS = mapOf(
        "janvier" to 1, "février" to 2, "fevrier" to 2, "mars" to 3, "avril" to 4,
        "mai" to 5, "juin" to 6, "juillet" to 7, "août" to 8, "aout" to 8,
        "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12, "decembre" to 12,
    )

    private val DATE_NUMERIC = Regex(
        """\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b"""
    )
    private val DATE_ISO = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
    private val DATE_TEXTUAL = Regex(
        """\b(\d{1,2})\s+(janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)\s+(\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")
    private val PRESCRIBER = Regex(
        """\bDr\.?\s+[A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]+(?:\s+[A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]+)?"""
    )
    private val DRUG = Regex(
        """\b([A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]{3,}(?:\s+[A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]{3,})?)\s+(\d{1,4}(?:[.,]\d+)?)\s?(mg|microgrammes?|µg|ug|g|UI|ml|%)\b"""
    )
    private val LABORATORY_LINE = Regex(
        """(?i)^.*\b(laboratoire|biologie|centre d[e']?examens?|radiolog|imagerie)\b.*$"""
    )

    private val DRUG_STOPWORDS = setOf(
        "date", "docteur", "laboratoire", "biologie", "adresse", "telephone",
        "téléphone", "patient", "docteur", "ordonnance", "analyse", "resultats",
        "résultats", "centre", "clinique", "cabinet", "validation", "page",
    )

    private val VACCINE_NAMES = listOf(
        "ROR" to "Rougeole, oreillons, rubéole",
        "DTP" to "Diphtérie, tétanos, poliomyélite",
        "Coqueluche" to "Coqueluche",
        "Hépatite B" to "Hépatite B",
        "Hepatite B" to "Hépatite B",
        "Pneumocoque" to "Infections invasives à pneumocoque",
        "Méningocoque" to "Infections invasives à méningocoque",
        "Meningocoque" to "Infections invasives à méningocoque",
        "Haemophilus" to "Haemophilus influenzae de type b",
        "Papillomavirus" to "Papillomavirus",
        "HPV" to "Papillomavirus",
        "Grippe" to "Grippe saisonnière",
        "Rotavirus" to "Gastro-entérites à rotavirus",
        "BCG" to "Tuberculose",
        "Zona" to "Zona",
    )

    private val EXAM_KEYWORDS = listOf(
        ExamCategory.BLOOD_TEST to Regex(
            """(?i)\b(glycémie|glycemie|hémogramme|hemogramme|NFS|bilan sanguin|bilan lipidique|ionogramme|cholestérol|cholesterol|CRP|TSH|créatinine|creatinine|ferritine|sérologie|serologie|hémoglobine|hemoglobine|plaquettes|leucocytes|transaminases|TGO|TGP|Gamma ?GT|urée|uree|acide urique|VS|PSA|facteur rhumatoïde|rhumatoide|TP ?INR|TCA|D[- ]dimères|dimers|frottis|ECBU|hémoculture|hemoculture|vitamine D|bilirubine|analyse sanguin)"""
        ),
        ExamCategory.RADIOLOGY to Regex(
            """(?i)\b(radiograph|radio thorax|rayons? ?x|mammograph|ost[ée]odensitom|densitom[ée]trie)"""
        ),
        ExamCategory.ULTRASOUND to Regex("""(?i)\b([ée]chograph|[ée]cho\b|doppler)"""),
        ExamCategory.MRI to Regex("""(?i)\b(IRM\b|r[ée]sonance magn[ée]tique)"""),
    )

    fun parse(text: String): ParsedFields {
        if (text.isBlank()) return ParsedFields()

        val dates = mutableListOf<LocalDate>()

        fun addDate(day: Int, month: Int, year: Int) {
            val fullYear = if (year < 100) 2000 + year else year
            if (month in 1..12) {
                val date = runCatching { LocalDate.of(fullYear, month, day) }.getOrNull()
                if (date != null && date !in dates) dates += date
            }
        }

        DATE_ISO.findAll(text).forEach { m ->
            addDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        DATE_NUMERIC.findAll(text).forEach { m ->
            addDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        DATE_TEXTUAL.findAll(text).forEach { m ->
            MONTHS[m.groupValues[2].lowercase()]?.let { month ->
                addDate(m.groupValues[1].toInt(), month, m.groupValues[3].toInt())
            }
        }

        val times = TIME.findAll(text)
            .mapNotNull { m ->
                runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
            }
            .distinct()
            .toList()

        val drugs = DRUG.findAll(text)
            .mapNotNull { m ->
                val name = m.groupValues[1].trim()
                if (name.lowercase() in DRUG_STOPWORDS || name.split(" ").any { it.lowercase() in DRUG_STOPWORDS }) {
                    null
                } else {
                    name to "${m.groupValues[2].replace(',', '.')} ${m.groupValues[3]}"
                }
            }
            .distinctBy { it.first.lowercase() }
            .take(6)
            .toList()

        val prescriber = PRESCRIBER.find(text)?.value?.replace(Regex("\\s+"), " ")?.trim()

        val laboratory = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.length <= 80 && LABORATORY_LINE.containsMatchIn(it) }

        val vaccine = VACCINE_NAMES.firstOrNull { (name, _) -> text.contains(name, ignoreCase = true) }

        val examCategory = EXAM_KEYWORDS.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

        return ParsedFields(
            dates = dates,
            times = times,
            drugs = drugs,
            prescriber = prescriber,
            laboratory = laboratory,
            vaccineName = vaccine?.first,
            disease = vaccine?.second,
            examCategory = examCategory,
        )
    }
}
