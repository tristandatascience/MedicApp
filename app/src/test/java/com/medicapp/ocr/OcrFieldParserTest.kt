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
    fun `texte vide ne produit rien`() {
        val parsed = OcrFieldParser.parse("   ")
        assertNull(parsed.prescriber)
        assertNull(parsed.laboratory)
        assertNull(parsed.vaccineName)
        assertTrue(parsed.dates.isEmpty())
    }
}
