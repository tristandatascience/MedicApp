package com.medicapp.data.repo

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilitairesTest {

    @Test
    fun `échappement des jokers SQL`() {
        assertEquals("%100\\%%", SearchRepository.escapeLike("100%"))
        assertEquals("%a\\_b%", SearchRepository.escapeLike("a_b"))
        assertEquals("%c\\\\d%", SearchRepository.escapeLike("c\\d"))
        assertEquals("%glycémie%", SearchRepository.escapeLike("glycémie"))
    }

    @Test
    fun `analyse des heures de prise`() {
        assertEquals(LocalTime.of(8, 0), DashboardRepository.parseTime("08:00"))
        assertEquals(LocalTime.of(20, 30), DashboardRepository.parseTime("20:30"))
        assertEquals(LocalTime.of(9, 0), DashboardRepository.parseTime("9"))
        assertNull(DashboardRepository.parseTime("abc"))
        assertNull(DashboardRepository.parseTime(""))
    }
}
