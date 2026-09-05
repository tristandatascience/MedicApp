package com.medicapp.data.storage

import com.medicapp.data.crypto.FileCipher
import java.io.File
import java.util.UUID

/** Stockage opaque des fichiers de documents (implémentation locale chiffrée ; Drive prévu en Option B). */
interface DocumentStore {
    fun save(bytes: ByteArray): String
    fun open(storageKey: String): ByteArray
    fun delete(storageKey: String)
    fun exists(storageKey: String): Boolean
}

/** Fichiers chiffrés AES-256/GCM dans l'espace privé de l'application. */
class LocalDocumentStore(baseDir: File, private val cipher: FileCipher) : DocumentStore {

    private val dir = File(baseDir, "documents").apply { mkdirs() }

    private fun fileFor(storageKey: String): File {
        // Clé générée par l'application uniquement ; vérification anti-traversée de chemin.
        require(KEY_PATTERN.matches(storageKey)) { "Clé de stockage invalide" }
        return File(dir, storageKey)
    }

    override fun save(bytes: ByteArray): String {
        val storageKey = UUID.randomUUID().toString().replace("-", "") + ".bin"
        fileFor(storageKey).writeBytes(cipher.encrypt(bytes))
        return storageKey
    }

    override fun open(storageKey: String): ByteArray {
        val file = fileFor(storageKey)
        require(file.exists()) { "Document introuvable" }
        return cipher.decrypt(file.readBytes())
    }

    override fun delete(storageKey: String) {
        fileFor(storageKey).delete()
    }

    override fun exists(storageKey: String): Boolean = fileFor(storageKey).exists()

    companion object {
        private val KEY_PATTERN = Regex("^[a-f0-9]{32}\\.bin$")
    }
}
