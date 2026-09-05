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
 *
 * Deux canaux : l'API GitHub, puis en secours la redirection de la page
 * « releases/latest » (utile si l'API est indisponible ou limitée en débit).
 */
object UpdateChecker {

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val apkUrl: String?,
        val notes: String,
    )

    /** Résultat : soit une release, soit un diagnostic d'échec. */
    data class Outcome(val info: ReleaseInfo?, val error: String?)

    private const val REPO = "tristandatascience/MedicApp"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val WEB_LATEST = "https://github.com/$REPO/releases/latest"

    suspend fun checkForUpdate(): Outcome = withContext(Dispatchers.IO) {
        val viaApi = runCatching { fetchViaApi() }
        viaApi.getOrNull()?.let { return@withContext Outcome(it, null) }

        val apiError = viaApi.exceptionOrNull()?.message ?: "API GitHub injoignable"
        val viaWeb = runCatching { fetchViaWebRedirect() }
        viaWeb.getOrNull()?.let { return@withContext Outcome(it, null) }

        val webError = viaWeb.exceptionOrNull()?.message ?: "page GitHub injoignable"
        Outcome(null, "API : $apiError — site : $webError")
    }

    private fun fetchViaApi(): ReleaseInfo? {
        val connection = URL(API_LATEST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = connection.responseCode
            if (code != 200) {
                throw IllegalStateException("HTTP $code" + if (code == 403) " (limite de requêtes atteinte, réessayez dans l'heure)" else "")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tagName = json.optString("tag_name")
            if (tagName.isBlank()) return null

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            return ReleaseInfo(
                tagName = tagName,
                versionName = tagName.removePrefix("v"),
                apkUrl = apkUrl,
                notes = json.optString("body").trim(),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Canal de secours : la page /releases/latest redirige vers /releases/tag/vX.Y.Z. */
    private fun fetchViaWebRedirect(): ReleaseInfo? {
        val connection = URL(WEB_LATEST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        try {
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (code !in 300..399 || location.isNullOrBlank()) {
                throw IllegalStateException("HTTP $code sans redirection")
            }
            val tagName = Regex("""tag/(v[\d.]+)""").find(location)?.groupValues?.get(1)
                ?: return null
            return ReleaseInfo(
                tagName = tagName,
                versionName = tagName.removePrefix("v"),
                apkUrl = "https://github.com/$REPO/releases/download/$tagName/app-debug.apk",
                notes = "",
            )
        } finally {
            connection.disconnect()
        }
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
