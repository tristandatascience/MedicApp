package com.medicapp.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Téléchargement de l'APK via le DownloadManager système (notification de
 * progression) puis lancement du programme d'installation du téléphone.
 */
object ApkInstaller {

    private const val APK_NAME = "maj-dossier-medical.apk"

    fun updatesDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
            .apply { mkdirs() }

    fun downloadedApk(context: Context): File = File(updatesDir(context), APK_NAME)

    /** Enregistre le téléchargement ; retourne l'identifiant DownloadManager. */
    fun download(context: Context, url: String): Long {
        val target = downloadedApk(context)
        target.delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Mise à jour — Dossier Médical")
            .setDescription("Téléchargement de la nouvelle version")
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${APK_NAME}",
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Android; DossierMedical)")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    /** Lance l'installation (le système peut demander d'autoriser les sources inconnues). */
    fun install(context: Context) {
        val apk = downloadedApk(context)
        if (!apk.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Programme d'installation absent : l'utilisateur installera via la
            // notification de téléchargement.
        }
    }

    /** Récepteur de fin de téléchargement, à désenregistrer en quittant l'écran. */
    fun registerCompletionReceiver(
        context: Context,
        downloadId: Long,
        onReady: () -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE &&
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == downloadId
                ) {
                    onReady()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }
}
