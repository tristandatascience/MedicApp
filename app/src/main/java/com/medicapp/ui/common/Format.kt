package com.medicapp.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatage français des dates et heures. */
object Format {
    private val locale = Locale.FRENCH

    private val dateLong: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val dateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val dateWithTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", locale)
    private val timeShort: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun date(date: LocalDate?): String = date?.format(dateLong) ?: "—"

    fun dateShort(date: LocalDate?): String = date?.format(dateShort) ?: "—"

    fun dateTime(dateTime: LocalDateTime?): String = dateTime?.format(dateWithTime) ?: "—"

    fun time(time: LocalTime?): String = time?.format(timeShort) ?: ""

    fun monthYear(date: LocalDate?): String = date?.format(monthYear)?.replaceFirstChar { it.uppercase(locale) } ?: "—"

    /** Ex. « aujourd'hui », « demain », « dans 3 jours », « il y a 2 jours ». */
    fun relativeFromToday(date: LocalDate, today: LocalDate): String {
        val days = date.toEpochDay() - today.toEpochDay()
        return when {
            days == 0L -> "aujourd'hui"
            days == 1L -> "demain"
            days == -1L -> "hier"
            days > 1 -> "dans $days jours"
            else -> "il y a ${-days} jours"
        }
    }
}
