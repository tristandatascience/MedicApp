package com.medicapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medicapp.MedicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Déclenchement d'un rappel unique (vaccination ou rendez-vous). */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        if (reminderId <= 0) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as MedicApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.database.reminderDao().getById(reminderId)?.let { reminder ->
                    NotificationsCenter.show(
                        appContext,
                        reminder.id.toInt(),
                        NotificationsCenter.CHANNEL_REMINDERS,
                        "Rappel dossier médical",
                        reminder.label,
                    )
                    container.database.reminderDao().markFired(reminder.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
