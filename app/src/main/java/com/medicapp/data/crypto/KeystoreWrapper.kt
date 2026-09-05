package com.medicapp.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction du enveloppement de clé par l'Android Keystore, injectable pour
 * les tests unitaires (le Keystore matériel n'existe pas sur JVM).
 */
interface KeystoreWrapper {
    /** Chiffre [plain] avec la clé du Keystore ; retourne iv (12 o) + ciphertext+tag. */
    fun wrap(plain: ByteArray): ByteArray

    /** Déchiffre un blob produit par [wrap] ; échoue si la clé Keystore a été invalidée. */
    fun unwrap(wrapped: ByteArray): ByteArray
}

/** Clé AES-256/GCM non exportable dans l'Android Keystore. */
class AndroidKeystoreWrapper : KeystoreWrapper {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun key(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_SIZE) { "Blob de clé invalide" }
        val iv = wrapped.copyOfRange(0, IV_SIZE)
        val ct = wrapped.copyOfRange(IV_SIZE, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "medic_master_wrap_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
