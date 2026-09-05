package com.medicapp.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage du code PIN : PBKDF2-HMAC-SHA256, 150 000 itérations, sel aléatoire.
 * Le PIN ne protège pas directement les données (la clé maître est dans le
 * Keystore) : il constitue la barrière d'entrée de l'application.
 */
class Pbkdf2PinHasher {

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparaison à temps constant. */
    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)

    companion object {
        private const val ITERATIONS = 150_000
        private const val HASH_BITS = 256
        private const val SALT_BYTES = 16
    }
}
