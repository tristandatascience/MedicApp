package com.medicapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medicapp.MedicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-planification de tous les rappels après le redémarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as MedicApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler.syncAll(appContext, container)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
