package com.medicapp.data.crypto

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Clé maître 256 bits : générée localement, persistée uniquement sous forme
 * chiffrée par la clé de l'Android Keystore ([KeystoreWrapper]).
 * Elle sert à dériver la phrase secrète SQLCipher et à chiffrer les fichiers.
 */
class MasterKeyManager(
    private val keystore: KeystoreWrapper,
    private val storageFile: File,
) {
    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    fun masterKey(): SecretKey {
        cached?.let { return it }
        val keyBytes = if (storageFile.exists()) {
            keystore.unwrap(storageFile.readBytes())
        } else {
            val fresh = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            storageFile.writeBytes(keystore.wrap(fresh))
            storageFile.setReadable(false, false)
            storageFile.setReadable(true, true)
            fresh
        }
        return SecretKeySpec(keyBytes, "AES").also { cached = it }
    }

    /** Phrase secrète SQLCipher : dérivée de la clé maître, jamais stockée. */
    fun databasePassphrase(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(masterKey().encoded + DB_PEPPER)
        return digest
    }

    /** Remplace la clé maître (restauration de sauvegarde) : ré-enveloppée par le Keystore local. */
    @Synchronized
    fun installKey(keyBytes: ByteArray) {
        require(keyBytes.size == KEY_SIZE_BYTES) { "Taille de clé invalide" }
        storageFile.writeBytes(keystore.wrap(keyBytes))
        cached = SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val KEY_SIZE_BYTES = 32
        private val DB_PEPPER = "medic.db/v1".toByteArray()
    }
}
