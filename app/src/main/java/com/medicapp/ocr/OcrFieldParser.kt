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
        /** Paires (nom du médicament, dosage détecté) — noms corrigés par le
         *  dictionnaire BDPM si fourni (« Dolipranne » → « DOLIPRANE »). */
        val drugs: List<Pair<String, String?>> = emptyList(),
        /**
         * Lignes d'actes prescrits sans dosage médicamenteux : analyses de
         * biologie, imagerie, kinésithérapie, soins infirmiers, orthophonie…
         */
        val prescribedItems: List<String> = emptyList(),
        /** Actes reconnus et normalisés par le dictionnaire (libellés canoniques). */
        val correctedActs: List<String> = emptyList(),
        val prescriber: String? = null,
        /** Spécialité médicale mentionnée (celle du prescripteur ou d'une orientation). */
        val specialty: String? = null,
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
        """\b([A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]{3,}(?:[ \t]+[A-ZÉÈÀÙ][A-Za-zÉÈéèêàçùîôûÄËÏÜ\-]{3,})?)[ \t]+(\d{1,4}(?:[.,]\d+)?)[ \t]?(mg|microgrammes?|µg|ug|g|UI|ml|%)\b"""
    )

    /** Dosage présent n'importe où sur une ligne (« 400 : 1 cp », « 1 g le matin »…). */
    private val DOSAGE_ON_LINE = Regex(
        """\b\d{1,4}(?:[.,]\d+)?\s?(?:mg|microgrammes?|microgramme|µg|ug|UI|ml|g)\b""",
        RegexOption.IGNORE_CASE,
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

    /** Actes paramédicaux et soins prescrits (hors médicaments et analyses). */
    private val PARAMEDICAL_KEYWORDS = Regex(
        """(?i)\b(kin[ée]sith[ée]rap|masso-?kin|s[ée]ances?|r[ée][ée]ducation|infirmi[èe]re?|IDE\b|pansements?|perfusion|injections?|orthophonie|orthoptie|ergoth[ée]rap|di[ée]t[ée]tici|podolog|psychologue|psychomotric|ost[ée]opath|sophrolog|audioproth|orth[ée]siste|p[ée]dicure|soins? de suite|hospitalisation)"""
    )

    /**
     * Spécialités par préfixe (couvre -logie / -logue / -logique) : celle du
     * prescripteur en en-tête ou celle d'une lettre d'orientation.
     */
    private val SPECIALTIES = listOf(
        "gastro-entérolog" to "Gastro-entérologie",
        "gastroentérolog" to "Gastro-entérologie",
        "gastroenterolog" to "Gastro-entérologie",
        "cardiolog" to "Cardiologie",
        "dermatolog" to "Dermatologie",
        "pneumolog" to "Pneumologie",
        "neurolog" to "Neurologie",
        "rhumatolog" to "Rhumatologie",
        "ophtalmolog" to "Ophtalmologie",
        "gynécolog" to "Gynécologie",
        "gynecolog" to "Gynécologie",
        "urolog" to "Urologie",
        "psychiatr" to "Psychiatrie",
        "endocrinolog" to "Endocrinologie",
        "hématolog" to "Hématologie",
        "hematolog" to "Hématologie",
        "oncolo" to "Oncologie",
        "néphrolog" to "Néphrologie",
        "nephrolog" to "Néphrologie",
        "allergolog" to "Allergologie",
        "stomatolog" to "Stomatologie",
        "addictolog" to "Addictologie",
        "gériatr" to "Gériatrie",
        "geriatr" to "Gériatrie",
        "pédiatr" to "Pédiatrie",
        "pediatr" to "Pédiatrie",
        "chirurgi" to "Chirurgie",
        "dentaire" to "Dentaire",
    )

    private val ORL_PATTERN = Regex("""(?i)\bORL\b|oto-rhino""")

    /**
     * Extraction des médicaments, ligne par ligne :
     * 1. motif « Nom + dosage attenant » (le dosage suit immédiatement le nom) ;
     * 2. avec dictionnaire BDPM : toute ligne contenant un nom de médicament
     *    connu (fenêtres de 1-2 mots capitalisés), le dosage étant cherché
     *    n'importe où sur la ligne — attrape les formats « Ibuprofene 400 :
     *    1 cp matin et soir » que le motif strict ignore.
     */
    private fun extractDrugs(
        text: String,
        dictionary: com.medicapp.data.medic.MedDictionary?,
    ): List<Pair<String, String?>> {
        val found = LinkedHashMap<String, Pair<String, String?>>()

        fun keyOf(name: String): String = name.lowercase()
            .replace("é", "e").replace("è", "e").replace("ê", "e").replace("à", "a")
            .replace("ç", "c").replace("î", "i").replace("ô", "o").replace("û", "u")

        fun add(name: String, dosage: String?) {
            val key = keyOf(name)
            if (found.size < 12 && key !in found.keys) found[key] = name to dosage
        }

        // Passe 1 : motif strict (nom capitalisé + dosage immédiat, même ligne).
        DRUG.findAll(text).forEach { match ->
            val name = match.groupValues[1].trim()
            val nameWords = name.lowercase().split(Regex("\\s+"))
            if (name.lowercase() !in DRUG_STOPWORDS && nameWords.none { it in DRUG_STOPWORDS }) {
                val corrected = dictionary?.correctDrug(name) ?: name
                add(corrected, "${match.groupValues[2].replace(',', '.')} ${match.groupValues[3]}")
            }
        }

        // Passe 2 : noms connus du dictionnaire BDPM sur chaque ligne.
        if (dictionary != null) {
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.length !in 3..120) return@forEach
                val words = line.split(Regex("\\s+"))
                    .map { it.trim(',', ';', ':', '.', '(', ')', '/') }
                    .filter { it.isNotBlank() }
                for (index in words.indices) {
                    val first = words[index]
                    if (first.length < 3 || !first[0].isUpperCase()) continue
                    dictionary.correctDrug(first)?.let { add(it, dosageOn(line)) }
                    // Fenêtre de 2 mots (« KLARICID LP »…).
                    if (index + 1 < words.size) {
                        val second = words[index + 1]
                        if (second.length >= 2 && second[0].isUpperCase()) {
                            dictionary.correctDrug("$first $second")?.let { add(it, dosageOn(line)) }
                        }
                    }
                }
            }
        }
        return found.values.take(10).toList()
    }

    private fun dosageOn(line: String): String? {
        DOSAGE_ON_LINE.find(line)?.let { return it.value.replace(",", ".") }
        // Sans unité explicite : premier nombre de 2-4 chiffres (« 400 »),
        // évite les quantités à 1 chiffre (« 1 cp », « 2/jour »).
        return Regex("""\b\d{2,4}\b""").find(line)?.value
    }

    fun parse(text: String, dictionary: com.medicapp.data.medic.MedDictionary? = null): ParsedFields {
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

        val drugs = extractDrugs(text, dictionary)

        val prescriber = PRESCRIBER.find(text)?.value?.replace(Regex("\\s+"), " ")?.trim()

        val laboratory = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.length <= 80 && LABORATORY_LINE.containsMatchIn(it) }

        val vaccine = VACCINE_NAMES.firstOrNull { (name, _) -> text.contains(name, ignoreCase = true) }

        val examCategory = EXAM_KEYWORDS.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

        // Spécialité : préfixe présent dans le texte (en-tête du prescripteur
        // ou lettre d'orientation vers un confrère).
        val specialty = SPECIALTIES.firstOrNull { (prefix, _) ->
            text.contains(prefix, ignoreCase = true)
        }?.second ?: if (ORL_PATTERN.containsMatchIn(text)) "ORL" else null

        // Lignes d'actes prescrits sans dosage : analyses, imagerie, kiné,
        // soins infirmiers, rééducation…
        val prescribedItems = text.lineSequence()
            .map { it.trim() }
            .filter { it.length in 3..90 }
            .filter { line ->
                EXAM_KEYWORDS.any { (_, regex) -> regex.containsMatchIn(line) } ||
                    PARAMEDICAL_KEYWORDS.containsMatchIn(line)
            }
            .filter { it != laboratory }
            .distinctBy { it.lowercase() }
            .take(10)
            .toList()

        // Actes normalisés par le dictionnaire (libellés canoniques) sur
        // toutes les lignes du document, pas seulement celles détectées.
        val correctedActs = if (dictionary != null) {
            text.lineSequence()
                .mapNotNull { line -> dictionary.correctAct(line.trim()) }
                .distinct()
                .take(10)
                .toList()
        } else {
            emptyList()
        }

        return ParsedFields(
            dates = dates,
            times = times,
            drugs = drugs,
            prescribedItems = prescribedItems,
            correctedActs = correctedActs,
            prescriber = prescriber,
            specialty = specialty,
            laboratory = laboratory,
            vaccineName = vaccine?.first,
            disease = vaccine?.second,
            examCategory = examCategory,
        )
    }
}
