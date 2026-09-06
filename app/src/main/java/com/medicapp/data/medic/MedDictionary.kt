package com.medicapp.data.medic

import android.content.Context
import java.text.Normalizer

/**
 * Dictionnaire médical français pour corriger les noms issus de l'OCR :
 * - médicaments : base publique officielle BDPM (marques + substances actives,
 *   asset « medicaments_fr.txt », ~13 000 noms) ;
 * - actes de biologie et d'imagerie : liste intégrée des examens courants.
 *
 * La correction se fait par rapprochement flou (distance de Levenshtein avec
 * seuil proportionnel à la longueur) sur des formes normalisées (sans
 * accents, minuscules, sans ponctuation). Traitement local instantané,
 * aucune IA ni réseau.
 */
class MedDictionary private constructor(
    private val drugEntries: Map<String, String>,
    private val actEntries: Map<String, String>,
) {

    /** Charge l'asset BDPM + les listes intégrées (une seule fois). */
    constructor(context: Context) : this(loadDrugs(context), ACTS.mapKeys { normalize(it.key) })

    /** Dictionnaire réduit pour les tests. */
    constructor(entries: List<String>) : this(
        entries.associateBy { normalize(it) },
        ACTS.mapKeys { normalize(it.key) },
    )

    @Volatile
    private var ready = drugEntries.isNotEmpty()

    fun ensureLoaded() {
        // Le constructeur charge déjà tout : garde pour compatibilité future.
    }

    /**
     * Corrige un nom de médicament détecté par l'OCR (« Dolipranne » →
     * « DOLIPRANE »). Retourne null si aucune entrée assez proche.
     */
    fun correctDrug(candidate: String): String? {
        val normalized = normalize(candidate)
        if (normalized.length < 3) return null
        drugEntries[normalized]?.let { return it }
        return bestMatch(normalized, drugEntries)
    }

    /**
     * Trouve un acte (biologie/imagerie) mentionné dans une ligne OCR.
     * Retourne le libellé canonique si la ligne contient un acte connu
     * (à près, pour l'OCR).
     */
    fun correctAct(line: String): String? {
        val normalizedLine = normalize(line)
        if (normalizedLine.isEmpty()) return null
        for ((key, display) in actEntries) {
            if (normalizedLine.contains(key)) return display
        }
        // Ligne courte : rapprochement flou direct sur le libellé complet.
        if (normalizedLine.length <= 40) {
            return bestMatch(normalizedLine, actEntries)
        }
        return null
    }

    private fun bestMatch(
        candidate: String,
        entries: Map<String, String>,
    ): String? {
        val threshold = when {
            candidate.length <= 4 -> 1
            candidate.length <= 8 -> 2
            else -> 3
        }
        var best: String? = null
        var bestDistance = threshold + 1
        for ((key, display) in entries) {
            if (key.length - candidate.length > threshold ||
                candidate.length - key.length > threshold
            ) {
                continue
            }
            val distance = levenshteinBounded(candidate, key, bestDistance)
            if (distance in 0 until bestDistance) {
                bestDistance = distance
                best = display
                if (distance == 0) break
            }
        }
        return best
    }

    companion object {
        /** Normalisation : minuscules, sans accents, uniquement alphanumérique. */
        fun normalize(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            val builder = StringBuilder(decomposed.length)
            for (char in decomposed) {
                if (char.isLetterOrDigit()) builder.append(char)
            }
            return builder.toString()
        }

        /** Distance de Levenshtein avec arrêt anticipé au-delà de [ceiling]. */
        fun levenshteinBounded(a: String, b: String, ceiling: Int): Int {
            if (a == b) return 0
            if (kotlin.math.abs(a.length - b.length) > ceiling) return ceiling + 1
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = current[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(
                        previous[j] + 1,
                        current[j - 1] + 1,
                        previous[j - 1] + cost,
                    )
                    if (current[j] < rowMin) rowMin = current[j]
                }
                if (rowMin > ceiling) return ceiling + 1
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }

        private fun loadDrugs(context: Context): Map<String, String> {
            return runCatching {
                context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.length >= 3) normalize(trimmed) to trimmed else null
                    }.toMap()
                }
            }.getOrDefault(emptyMap())
        }

        private const val ASSET_NAME = "medicaments_fr.txt"

        /** Actes de biologie et d'imagerie courants (libellés canoniques). */
        val ACTS: Map<String, String> = linkedMapOf(
            // Biologie
            "NFS" to "NFS (numération formule sanguine)",
            "hémogramme" to "NFS (numération formule sanguine)",
            "glycémie à jeun" to "Glycémie à jeun",
            "hba1c" to "HbA1c (hémoglobine glyquée)",
            "bilan lipidique" to "Bilan lipidique",
            "cholestérol" to "Cholestérol",
            "triglycérides" to "Triglycérides",
            "asat" to "ASAT (transaminases)",
            "alat" to "ALAT (transaminases)",
            "gamma gt" to "Gamma GT",
            "bilirubine" to "Bilirubine",
            "phosphatases alcalines" to "Phosphatases alcalines",
            "créatinine" to "Créatininémie",
            "clairance" to "Clairance de la créatinine",
            "urée" to "Urémie",
            "acide urique" to "Acide urique",
            "ionogramme" to "Ionogramme sanguin",
            "natrémie" to "Natrémie",
            "kalémie" to "Kaliémie",
            "calcémie" to "Calcémie",
            "crp" to "CRP",
            "vs" to "VS (vitesse de sédimentation)",
            "tsh" to "TSH",
            "t4l" to "T4 libre",
            "ferritine" to "Ferritinémie",
            "fer sérique" to "Fer sérique",
            "hémoglobine" to "Hémoglobine",
            "groupe sanguin" to "Groupe sanguin",
            "rai" to "RAI (recherche agglutinines irrégulières)",
            "tp inr" to "TP INR",
            "tca" to "TCA",
            "d-dimères" to "D-dimères",
            "fibrinogène" to "Fibrinogène",
            "vitamine d" to "Vitamine D",
            "vitamine b12" to "Vitamine B12",
            "folates" to "Folates",
            "psa" to "PSA",
            "hcg" to "Beta HCG",
            "sérologie" to "Sérologie",
            "hépatite b" to "Sérologie hépatite B",
            "hépatite c" to "Sérologie hépatite C",
            "vih" to "Sérologie VIH",
            "toxoplasmose" to "Sérologie toxoplasmose",
            "rubéole" to "Sérologie rubéole",
            "ecbu" to "ECBU",
            "hémoculture" to "Hémoculture",
            "frottis" to "Frottis",
            "coproculture" to "Coproculture",
            "sang occulte" to "Recherche de sang occulte dans les selles",
            "transferrine" to "Transferrine",
            "albumine" to "Albuminémie",
            "cpk" to "CPK",
            "ldh" to "LDH",
            "lipase" to "Lipase",
            "amylase" to "Amylase",
            "pth" to "PTH",
            "cortisol" to "Cortisol",
            "prolactine" to "Prolactine",
            "fs lh" to "FSH LH",
            "bilan martial" to "Bilan martial",
            "bilan hépatique" to "Bilan hépatique",
            "bilan rénal" to "Bilan rénal",
            "bilan thyroïdien" to "Bilan thyroïdien",
            // Imagerie et explorations
            "radiographie thoracique" to "Radiographie thoracique",
            "radio thorax" to "Radiographie thoracique",
            "radiographie" to "Radiographie",
            "mammographie" to "Mammographie",
            "densitométrie" to "Densitométrie osseuse",
            "échographie abdominale" to "Échographie abdominale",
            "échographie pelvienne" to "Échographie pelvienne",
            "échographie thyroïdienne" to "Échographie thyroïdienne",
            "échographie cardiaque" to "Échographie cardiaque",
            "échographie" to "Échographie",
            "echo doppler" to "Écho-doppler",
            "doppler" to "Doppler",
            "irm cérébrale" to "IRM cérébrale",
            "irm" to "IRM",
            "scanner cérébral" to "Scanner cérébral",
            "scanner thoracique" to "Scanner thoracique",
            "scanner abdominal" to "Scanner abdominal",
            "scanner" to "Scanner (TDM)",
            "scintigraphie" to "Scintigraphie",
            "angiographie" to "Angiographie",
            "ecg" to "Électrocardiogramme (ECG)",
            "holter" to "Holter ECG",
            "épreuve d'effort" to "Épreuve d'effort",
            "fibroscopie" to "Fibroscopie",
            "coloscopie" to "Coloscopie",
        )
    }
}
