package com.medicapp.data.repo

import com.medicapp.data.db.dao.ReminderDao
import com.medicapp.data.db.entity.Reminder
import com.medicapp.data.db.entity.ReminderKind
import com.medicapp.data.prefs.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Génère les lignes de rappel (notifications) associées aux vaccinations et
 * rendez-vous. Les rappels de prise de traitement sont gérés séparément par
 * une alarme quotidienne qui lit les traitements actifs.
 */
class ReminderPlanner(
    private val reminderDao: ReminderDao,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    suspend fun regenerateVaccineBoosterReminders(
        profileId: Long,
        vaccinationId: Long,
        vaccineName: String,
        nextDueDate: LocalDate?,
    ) {
        reminderDao.deletePendingByReference(ReminderKind.VACCINE_BOOSTER, vaccinationId)
        val due = nextDueDate ?: return
        val now = LocalDateTime.now(clock)
        settings.current().vaccineReminderDays
            .map { due.minusDays(it.toLong()).atTime(9, 0) }
            .filter { it.isAfter(now) }
            .forEach { triggerAt ->
                reminderDao.insert(
                    Reminder(
                        profileId = profileId,
                        kind = ReminderKind.VACCINE_BOOSTER,
                        referenceId = vaccinationId,
                        label = "Rappel de vaccination : « $vaccineName » prévu le ${due.format(dateFormat)}",
                        triggerAt = triggerAt,
                    )
                )
            }
    }

    suspend fun regenerateAppointmentReminders(
        profileId: Long,
        appointmentId: Long,
        label: String,
        dateTime: LocalDateTime,
    ) {
        reminderDao.deletePendingByReference(ReminderKind.APPOINTMENT, appointmentId)
        val now = LocalDateTime.now(clock)
        settings.current().appointmentReminderOffsetsMin
            .map { dateTime.minusMinutes(it.toLong()) }
            .filter { it.isAfter(now) }
            .forEach { triggerAt ->
                reminderDao.insert(
                    Reminder(
                        profileId = profileId,
                        kind = ReminderKind.APPOINTMENT,
                        referenceId = appointmentId,
                        label = "Rendez-vous : $label à ${dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        triggerAt = triggerAt,
                    )
                )
            }
    }

    suspend fun deletePending(kind: ReminderKind, referenceId: Long) =
        reminderDao.deletePendingByReference(kind, referenceId)
}
