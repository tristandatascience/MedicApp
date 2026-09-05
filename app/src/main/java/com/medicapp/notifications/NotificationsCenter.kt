package com.medicapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medicapp.MainActivity
import com.medicapp.R

/** Création et affichage des notifications de rappel. */
object NotificationsCenter {

    const val CHANNEL_REMINDERS = "rappels"
    const val CHANNEL_INTAKES = "prises"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Rappels (vaccins, rendez-vous)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Rappels de vaccination et de rendez-vous" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INTAKES,
                "Prises de traitement",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Notifications de prise des traitements en cours" }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, channel: String, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
