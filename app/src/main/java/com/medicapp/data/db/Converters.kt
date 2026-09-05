package com.medicapp.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Converters {

    private val listSeparator = '\u001F' // séparateur d'unité, absent des textes saisis

    // ---- Dates ----

    @TypeConverter
    fun localDateFromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun localDateTimeFromMillis(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

    @TypeConverter
    fun localDateTimeToMillis(value: LocalDateTime?): Long? =
        value?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    // ---- Listes de chaînes ----

    @TypeConverter
    fun stringListFromString(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(listSeparator)

    /** Jamais null : les colonnes de listes sont NOT NULL. */
    @TypeConverter
    fun stringListToString(value: List<String>?): String =
        value?.joinToString(listSeparator.toString()) ?: ""
}
