package com.medicapp.data.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement AES-256/GCM des fichiers numérisés, en mémoire (les documents
 * scannés font quelques Mo au maximum).
 * Format : [version 1 o][IV 12 o][ciphertext + tag GCM 16 o].
 */
class FileCipher(private val keyProvider: () -> SecretKey) {

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        return byteArrayOf(FORMAT_VERSION) + iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 1 + IV_SIZE) { "Fichier chiffré invalide" }
        require(blob[0] == FORMAT_VERSION) { "Version de format inconnue : ${blob[0]}" }
        val iv = blob.copyOfRange(1, 1 + IV_SIZE)
        val ct = blob.copyOfRange(1 + IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}
