package com.medicapp.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medicapp.MedicApplication
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Alarme quotidienne de prise de traitement : notifie les médicaments du
 * créneau horaire puis replanifie le créneau pour le lendemain
 * (notifications locales hors ligne, § 4.3).
 */
class TreatmentIntakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timeLabel = intent.getStringExtra("intake_time") ?: return
        val parsed = runCatching {
            val parts = timeLabel.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }.getOrNull() ?: return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as MedicApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = container.settings.current()
                val today = LocalDate.now()
                if (settings.currentProfileId != 0L) {
                    container.database.treatmentDao()
                        .observeActive(settings.currentProfileId, today.toEpochDay())
                        .first()
                        .filter { it.isActive(today) && it.startDate <= today && timeLabel in it.intakeTimes }
                        .forEach { treatment ->
                            NotificationsCenter.show(
                                appContext,
                                ("intake_${treatment.id}_$timeLabel").hashCode(),
                                NotificationsCenter.CHANNEL_INTAKES,
                                "Prise de traitement",
                                "${treatment.drugName}" +
                                    (treatment.dosage?.let { " — $it" } ?: "") +
                                    " (${timeLabel})",
                            )
                        }
                }

                // Replanification du créneau pour le lendemain.
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                ReminderScheduler.scheduleNextIntake(appContext, alarmManager, parsed)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
