package com.medicapp.data.backup

import android.content.Context
import android.net.Uri
import com.medicapp.di.AppContainer
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sauvegarde et restauration complètes du dossier (§ 5.1, § 6) :
 * archive ZIP (base SQLCipher + documents chiffrés + clé maître) elle-même
 * chiffrée avec un mot de passe choisi par l'utilisateur, à conserver hors du
 * téléphone. Contenu :
 * - manifest.json     : signature et version du format
 * - key.bin           : clé maître chiffrée par le mot de passe de l'archive
 * - medic.db          : base de données chiffrée (SQLCipher, clé maître)
 * - docs/<clé>        : fichiers de documents chiffrés (AES-256/GCM)
 * - pin.json          : code PIN (hash PBKDF2) pour retrouver l'accès
 */
class BackupManager(
    private val context: Context,
    private val container: AppContainer,
) {

    data class Progress(val step: String)

    suspend fun exportTo(uri: Uri, password: String, onProgress: (Progress) -> Unit) {
        onProgress(Progress("Préparation de la base…"))

        // Copie cohérente sans fermer la base : VACUUM INTO.
        val tempDir = File(context.cacheDir, "backup-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val dbCopy = File(tempDir, ENTRY_DB)
        container.database.openHelper.writableDatabase
            .query("VACUUM INTO '${dbCopy.absolutePath.replace("'", "''")}'")
            .use { it.moveToFirst() }

        onProgress(Progress("Lecture des documents…"))
        val zipTemp = File(tempDir, "archive.zip")
        ZipOutputStream(zipTemp.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(MANIFEST.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // Clé maître protégée par le mot de passe de l'archive.
            zip.putNextEntry(ZipEntry(ENTRY_KEY))
            zip.write(PasswordCrypto.encrypt(password, container.masterKeyManager.masterKey().encoded))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_DB))
            zip.write(dbCopy.readBytes())
            zip.closeEntry()

            val docsDir = File(context.filesDir, "documents")
            if (docsDir.isDirectory) {
                docsDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("docs/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        onProgress(Progress("Chiffrement de l'archive…"))
        val plain = zipTemp.readBytes()
        val encrypted = PasswordCrypto.encrypt(password, plain)

        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(encrypted)
            output.flush()
        }

        tempDir.deleteRecursively()
        onProgress(Progress("Sauvegarde terminée"))
    }

    /**
     * Restaure une archive : remplace l'intégralité des données du téléphone.
     * L'application est ensuite redémarrée.
     */
    suspend fun importFrom(uri: Uri, password: String, onProgress: (Progress) -> Unit): Boolean {
        onProgress(Progress("Lecture de l'archive…"))
        val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return false
        val zipBytes = try {
            PasswordCrypto.decrypt(password, blob)
        } catch (e: Exception) {
            return false // mot de passe incorrect ou archive corrompue
        }

        val tempDir = File(context.cacheDir, "restore-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        var masterKey: ByteArray? = null
        var hasDb = false

        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == ENTRY_KEY -> masterKey = PasswordCrypto.decrypt(password, zip.readBytes())
                    entry.name == ENTRY_DB -> {
                        File(tempDir, ENTRY_DB).writeBytes(zip.readBytes())
                        hasDb = true
                    }
                    entry.name.startsWith("docs/") -> {
                        val name = entry.name.removePrefix("docs/")
                        if (name.isNotBlank()) File(tempDir, "docs-$name").writeBytes(zip.readBytes())
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (!hasDb || masterKey == null) {
            tempDir.deleteRecursively()
            return false
        }

        onProgress(Progress("Restauration des données…"))
        val key = masterKey!!

        // Fermeture de la base courante, remplacement des fichiers,
        // puis installation de la clé maître restaurée dans le Keystore local.
        container.resetDatabase()
        listOf("medic.db", "medic.db-wal", "medic.db-shm").forEach { name ->
            context.getDatabasePath(name).delete()
        }
        val databasesDir = context.getDatabasePath("medic.db").parentFile
        databasesDir?.mkdirs()
        File(databasesDir, "medic.db").writeBytes(File(tempDir, ENTRY_DB).readBytes())

        File(context.filesDir, "documents").deleteRecursively()
        val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
        tempDir.listFiles()?.filter { it.name.startsWith("docs-") }?.forEach { file ->
            file.copyTo(File(docsDir, file.name.removePrefix("docs-")), overwrite = true)
        }

        container.installMasterKey(key)
        tempDir.deleteRecursively()

        // Les rappels seront re-planifiés au prochain démarrage de l'application.
        onProgress(Progress("Restauration terminée"))
        return true
    }

    companion object {
        private const val ENTRY_MANIFEST = "manifest.json"
        private const val ENTRY_KEY = "key.bin"
        private const val ENTRY_DB = "medic.db"
        private val MANIFEST = """{"app":"DossierMedical","format":1}"""
    }
}
