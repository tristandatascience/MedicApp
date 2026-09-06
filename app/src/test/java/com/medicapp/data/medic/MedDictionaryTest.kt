package com.medicapp.data.medic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedDictionaryTest {

    private val dictionary = MedDictionary(
        listOf("DOLIPRANE", "PARACETAMOL", "AMOXICILLINE", "VENTOLINE")
    )

    @Test
    fun `normalisation sans accents ni ponctuation`() {
        assertEquals("echographieabdominale", MedDictionary.normalize("Échographie abdominale"))
        assertEquals("nfs", MedDictionary.normalize("N.F.S !"))
        assertEquals("doliprane", MedDictionary.normalize("Doliprane"))
    }

    @Test
    fun `distance de levenshtein bornee`() {
        assertEquals(0, MedDictionary.levenshteinBounded("abc", "abc", 2))
        assertEquals(1, MedDictionary.levenshteinBounded("abc", "abd", 2))
        assertEquals(1, MedDictionary.levenshteinBounded("dolipranne", "doliprane", 3))
        assertEquals(2, MedDictionary.levenshteinBounded("amoxicillme", "amoxicilline", 3))
        // au-delà du plafond : réponse > plafond, sans calcul complet
        assertTrue(MedDictionary.levenshteinBounded("aaaa", "zzzz", 2) > 2)
    }

    @Test
    fun `correction d'un nom mal lu par l'ocr`() {
        assertEquals("DOLIPRANE", dictionary.correctDrug("Dolipranne"))
        assertEquals("DOLIPRANE", dictionary.correctDrug("doliprane"))
        assertEquals("AMOXICILLINE", dictionary.correctDrug("Amoxicillme"))
        assertNull(dictionary.correctDrug("zzzz"))
    }

    @Test
    fun `acte de biologie reconnu dans une ligne`() {
        assertEquals("NFS (numération formule sanguine)", dictionary.correctAct("NFS plaquettes"))
        assertEquals("Bilan lipidique", dictionary.correctAct("Bilan lipidique complet"))
        assertEquals("Échographie abdominale", dictionary.correctAct("échographie abdominale à jour"))
    }

    @Test
    fun `acte flou rapproché du libellé canonique`() {
        assertEquals("CRP", dictionary.correctAct("CRP"))
        assertEquals("TSH", dictionary.correctAct("T.S.H"))
    }
}
