package com.medicapp.ocr

import com.medicapp.data.db.entity.ExamCategory
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFieldParserTest {

    @Test
    fun `dates aux formats français reconnues`() {
        val parsed = OcrFieldParser.parse(
            """
            Ordonnance
            Le 12/03/2025, consultation.
            Contrôle prévu le 3 juin 2025.
            """.trimIndent()
        )
        assertTrue(LocalDate.of(2025, 3, 12) in parsed.dates)
        assertTrue(LocalDate.of(2025, 6, 3) in parsed.dates)
    }

    @Test
    fun `date ISO reconnue`() {
        val parsed = OcrFieldParser.parse("Prélèvement du 2024-11-30 effectué")
        assertTrue(LocalDate.of(2024, 11, 30) in parsed.dates)
    }

    @Test
    fun `date invalide ignorée`() {
        val parsed = OcrFieldParser.parse("Le 31/02/2024 n'existe pas")
        assertTrue(parsed.dates.isEmpty())
    }

    @Test
    fun `heures reconnues`() {
        val parsed = OcrFieldParser.parse("Rendez-vous à 14h30, accueil 9:15")
        assertTrue(LocalTime.of(14, 30) in parsed.times)
        assertTrue(LocalTime.of(9, 15) in parsed.times)
    }

    @Test
    fun `médicament et dosage reconnus`() {
        val parsed = OcrFieldParser.parse("Doliprane 1000 mg : 1 comprimé. Amoxicilline 500 mg")
        val names = parsed.drugs.map { it.first }
        assertTrue("Doliprane" in names)
        assertTrue("Amoxicilline" in names)
        assertEquals("1000 mg", parsed.drugs.first { it.first == "Doliprane" }.second)
    }

    @Test
    fun `prescripteur reconnu`() {
        val parsed = OcrFieldParser.parse("Dr. Marie Bernard — Médecin généraliste")
        assertEquals("Dr. Marie Bernard", parsed.prescriber)
    }

    @Test
    fun `laboratoire reconnu sur sa ligne`() {
        val parsed = OcrFieldParser.parse(
            """
            Résultats
            Laboratoire Biolys - 12 rue des Lilas
            Glycémie à jeun : 0,92 g/L
            """.trimIndent()
        )
        assertEquals("Laboratoire Biolys - 12 rue des Lilas", parsed.laboratory)
        assertEquals(ExamCategory.BLOOD_TEST, parsed.examCategory)
    }

    @Test
    fun `catégorie échographie reconnue`() {
        val parsed = OcrFieldParser.parse("Compte rendu d'échographie abdominale")
        assertEquals(ExamCategory.ULTRASOUND, parsed.examCategory)
    }

    @Test
    fun `vaccin reconnu`() {
        val parsed = OcrFieldParser.parse("Vaccination ROR effectuée, rappel dans 10 ans")
        assertEquals("ROR", parsed.vaccineName)
        assertEquals("Rougeole, oreillons, rubéole", parsed.disease)
    }

    @Test
    fun `lignes d'analyses prescrites reconnues (ordonnance de biologie)`() {
        val parsed = OcrFieldParser.parse(
            """
            Dr. Paul Martin
            Ordonnance de biologie du 12/03/2025
            NFS plaquettes
            Glycémie à jeun
            Bilan lipidique complet
            Ionogramme sanguin
            """.trimIndent()
        )
        assertEquals("Dr. Paul Martin", parsed.prescriber)
        assertTrue(LocalDate.of(2025, 3, 12) in parsed.dates)
        assertEquals(4, parsed.prescribedItems.size)
        assertTrue(parsed.prescribedItems.any { it.contains("Glycémie") })
        assertTrue(parsed.prescribedItems.any { it.contains("Bilan lipidique") })
    }

    @Test
    fun `ordonnance de kinésithérapie reconnue`() {
        val parsed = OcrFieldParser.parse(
            """
            Dr. Sophie Lambert — Médecin généraliste
            Kinésithérapie : 10 séances
            Rééducation de l'épaule droite
            À renouveler si besoin
            """.trimIndent()
        )
        assertEquals("Dr. Sophie Lambert", parsed.prescriber)
        assertTrue(parsed.prescribedItems.any { it.contains("Kinésithérapie") })
        assertTrue(parsed.prescribedItems.any { it.contains("Rééducation") })
    }

    @Test
    fun `soins infirmiers reconnus`() {
        val parsed = OcrFieldParser.parse(
            """
            Dr. Martin
            Infirmière : pansements quotidiens
            Injection quotidienne
            """.trimIndent()
        )
        assertTrue(parsed.prescribedItems.any { it.contains("Infirmière") })
        assertTrue(parsed.prescribedItems.any { it.contains("Injection") })
    }

    @Test
    fun `lettre d'orientation vers un spécialiste reconnue`() {
        val parsed = OcrFieldParser.parse(
            """
            Je vous prie de bien vouloir recevoir mon patient
            pour un avis cardiologique dans le cadre de...
            """.trimIndent()
        )
        assertEquals("Cardiologie", parsed.specialty)
    }

    @Test
    fun `spécialité du prescripteur reconnue en en-tête`() {
        val parsed = OcrFieldParser.parse("Dr. Marie Bernard\nPneumologue\nConsultation du 03/06/2025")
        assertEquals("Pneumologie", parsed.specialty)
    }

    @Test
    fun `nom de médicament corrigé par le dictionnaire BDPM`() {
        val dictionary = com.medicapp.data.medic.MedDictionary(listOf("DOLIPRANE"))
        val parsed = OcrFieldParser.parse("Dolipranne 1000 mg : 1 comprimé", dictionary)
        assertEquals("DOLIPRANE", parsed.drugs.first().first)
        assertEquals("1000 mg", parsed.drugs.first().second)
    }

    @Test
    fun `actes normalisés par le dictionnaire`() {
        val dictionary = com.medicapp.data.medic.MedDictionary(emptyList())
        val parsed = OcrFieldParser.parse(
            """
            Ordonnance de biologie
            NFS plaquettes
            Bilan lipidique complet
            Ionogramme
            """.trimIndent(),
            dictionary,
        )
        assertTrue(parsed.correctedActs.contains("NFS (numération formule sanguine)"))
        assertTrue(parsed.correctedActs.contains("Bilan lipidique"))
    }

    @Test
    fun `plusieurs médicaments capturés sur toutes les lignes`() {
        val dictionary = com.medicapp.data.medic.MedDictionary(
            listOf("DOLIPRANE", "IBUPROFENE", "AMOXICILLINE", "SMECTA")
        )
        val parsed = OcrFieldParser.parse(
            """
            Ordonnance
            DOLIPRANE 1000 mg : 1 cp matin et soir
            Ibuprofene 400 : 1 cp midi
            AMOXICILLINE 1 g : 2/jour pendant 6 jours
            Smecta 3 sachets par jour
            """.trimIndent(),
            dictionary,
        )
        val names = parsed.drugs.map { it.first }
        assertTrue("médicaments reçus : $names", names.size == 4)
        assertTrue("DOLIPRANE" in names)
        assertTrue("IBUPROFENE" in names)
        assertTrue("AMOXICILLINE" in names)
        assertTrue("SMECTA" in names)
    }

    @Test
    fun `dosage recherché n'importe où sur la ligne`() {
        val dictionary = com.medicapp.data.medic.MedDictionary(listOf("IBUPROFENE"))
        val parsed = OcrFieldParser.parse("Ibuprofene 400 : 1 cp matin et soir", dictionary)
        assertEquals("IBUPROFENE", parsed.drugs.first().first)
        assertEquals("400", parsed.drugs.first().second)
    }

    @Test
    fun `texte vide ne produit rien`() {
        val parsed = OcrFieldParser.parse("   ")
        assertNull(parsed.prescriber)
        assertNull(parsed.laboratory)
        assertNull(parsed.specialty)
        assertNull(parsed.vaccineName)
        assertTrue(parsed.dates.isEmpty())
    }
}
