package com.medicapp.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérification des mises à jour depuis les releases GitHub du dépôt.
 * Requête ponctuelle, déclenchée uniquement par l'utilisateur depuis les
 * réglages : aucune donnée de santé n'est envoyée (requête GET anonyme).
 */
object UpdateChecker {

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val apkUrl: String?,
        val notes: String,
    )

    private const val LATEST_URL =
        "https://api.github.com/repos/tristandatascience/MedicApp/releases/latest"

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.instanceFollowRedirects = true
            try {
                if (connection.responseCode != 200) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tagName = json.optString("tag_name")
                if (tagName.isBlank()) return@runCatching null

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets") ?: return@runCatching null
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                ReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    apkUrl = apkUrl,
                    notes = json.optString("body").trim(),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Compare des versions numériques (ex. 1.10.0 > 1.9.0). */
    fun isNewer(current: String, candidate: String): Boolean {
        val a = current.split('.').map { it.toIntOrNull() ?: 0 }
        val b = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }
}
