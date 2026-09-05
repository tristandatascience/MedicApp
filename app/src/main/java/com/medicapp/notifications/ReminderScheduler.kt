package com.medicapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medicapp.data.db.entity.Reminder
import com.medicapp.data.repo.DashboardRepository
import com.medicapp.di.AppContainer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Planification des rappels locaux (§ 4.2, 4.3, 4.6) :
 * - rappels de vaccination et de rendez-vous : alarmes exactes uniques ;
 * - prises de traitement : une alarme quotidienne par créneau horaire,
 *   re-planifiée à chaque déclenchement (fonctionne hors ligne).
 */
object ReminderScheduler {

    private const val EXTRA_REMINDER_ID = "reminder_id"
    private const val EXTRA_INTAKE_TIME = "intake_time"

    /** Replanifie tout ce qui est en attente : appelé au démarrage de l'app et après BOOT. */
    suspend fun syncAll(context: Context, container: AppContainer) {
        NotificationsCenter.ensureChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        // 1. Rappels uniques (vaccins, rendez-vous) non encore planifiés ou dépassés.
        container.database.reminderDao().toSchedule(now).forEach { reminder ->
            scheduleOneShot(context, alarmManager, reminder)
            container.database.reminderDao().markScheduled(reminder.id, true)
        }
        // Rappels dont l'heure est déjà passée sans être déclenchés : notification immédiate.
        container.database.reminderDao().allPending()
            .filter { it.triggerAt.isBefore(LocalDateTime.now()) }
            .forEach { reminder ->
                NotificationsCenter.show(
                    context,
                    reminder.id.toInt(),
                    NotificationsCenter.CHANNEL_REMINDERS,
                    "Rappel dossier médical",
                    reminder.label,
                )
                container.database.reminderDao().markFired(reminder.id)
            }

        // 2. Prises de traitement du jour et des jours suivants.
        scheduleIntakeAlarms(context, container, alarmManager)
    }

    private fun scheduleOneShot(context: Context, alarmManager: AlarmManager, reminder: Reminder) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, reminder.id)
        val pending = PendingIntent.getBroadcast(
            context,
            (REMINDER_PREFIX + reminder.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = reminder.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setExactBestEffort(alarmManager, triggerAt, pending)
    }

    /** Une alarme quotidienne par créneau horaire distinct des traitements actifs. */
    suspend fun scheduleIntakeAlarms(context: Context, container: AppContainer, alarmManager: AlarmManager) {
        val settings = container.settings.current()
        if (settings.currentProfileId == 0L) return

        val today = LocalDate.now()
        val times = mutableListOf<String>()
        container.database.treatmentDao()
            .observeActive(settings.currentProfileId, today.toEpochDay())
            .first()
            .filter { it.isActive(today) && it.startDate <= today }
            .forEach { treatment -> times += treatment.intakeTimes }

        times.distinct().forEach { time ->
            val parsed = DashboardRepository.parseTime(time) ?: return@forEach
            scheduleNextIntake(context, alarmManager, parsed)
        }
    }

    /** Planifie la prochaine occurrence (aujourd'hui ou demain) d'un créneau de prise. */
    fun scheduleNextIntake(context: Context, alarmManager: AlarmManager, time: LocalTime) {
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val intent = Intent(context, TreatmentIntakeReceiver::class.java)
            .putExtra(EXTRA_INTAKE_TIME, "${time.hour}:${time.minute}")
        val pending = PendingIntent.getBroadcast(
            context,
            (INTAKE_PREFIX + "${time.hour}:${time.minute}").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExactBestEffort(alarmManager, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
    }

    private fun setExactBestEffort(alarmManager: AlarmManager, triggerAtMillis: Long, pending: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            // Alarmes exactes non autorisées : fenêtre d'une minute (dégradation douce).
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 60_000L, pending)
        }
    }

    private const val REMINDER_PREFIX = "reminder_"
    private const val INTAKE_PREFIX = "intake_"
}
