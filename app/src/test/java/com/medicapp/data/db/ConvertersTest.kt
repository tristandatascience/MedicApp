package com.medicapp.data.db

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `aller-retour dates`() {
        val date = LocalDate.of(2026, 3, 12)
        assertEquals(date, converters.localDateFromEpochDay(converters.localDateToEpochDay(date)))
        assertEquals(null, converters.localDateToEpochDay(null))
        assertEquals(null, converters.localDateFromEpochDay(null))
    }

    @Test
    fun `aller-retour dates-heures`() {
        val dateTime = LocalDateTime.of(2026, 3, 12, 14, 30)
        assertEquals(dateTime, converters.localDateTimeFromMillis(converters.localDateTimeToMillis(dateTime)))
    }

    @Test
    fun `aller-retour listes de chaînes`() {
        val list = listOf("Carte vitale", "Ordonnance — Dr Martin", "08:00")
        assertEquals(list, converters.stringListFromString(converters.stringListToString(list)))
    }

    @Test
    fun `listes vides`() {
        assertEquals(emptyList<String>(), converters.stringListFromString(null))
        assertEquals(emptyList<String>(), converters.stringListFromString(""))
        // les colonnes de listes sont NOT NULL : conversion en chaîne vide
        assertEquals("", converters.stringListToString(emptyList()))
        assertEquals("", converters.stringListToString(null))
    }
}
