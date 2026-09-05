package com.medicapp.data.backup

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement dérivé d'un mot de passe (PBKDF2-HMAC-SHA256 + AES-256/GCM),
 * utilisé pour les archives de sauvegarde exportées hors du téléphone.
 * Format : [sel 16 o][IV 12 o][ciphertext + tag].
 */
object PasswordCrypto {

    fun encrypt(password: String, plain: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return salt + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(password: String, blob: ByteArray): ByteArray {
        require(blob.size > SALT_BYTES + IV_BYTES) { "Archive invalide" }
        val salt = blob.copyOfRange(0, SALT_BYTES)
        val iv = blob.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val ct = blob.copyOfRange(SALT_BYTES + IV_BYTES, blob.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ITERATIONS = 200_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
}
