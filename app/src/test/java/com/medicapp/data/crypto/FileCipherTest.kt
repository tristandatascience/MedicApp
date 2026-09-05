package com.medicapp.data.crypto

import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCipherTest {

    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val cipher = FileCipher { key }

    @Test
    fun `chiffrement puis déchiffrement retrouve les données`() {
        val plain = "Résultats d'analyse — glycémie à jeun".toByteArray()
        val encrypted = cipher.encrypt(plain)
        assertFalse(encrypted.contentEquals(plain))
        assertArrayEquals(plain, cipher.decrypt(encrypted))
    }

    @Test
    fun `le chiffrement produit des sorties différentes à chaque appel`() {
        val plain = "ordonnance".toByteArray()
        assertFalse(cipher.encrypt(plain).contentEquals(cipher.encrypt(plain)))
    }

    @Test
    fun `altération du ciphertext détectée`() {
        val encrypted = cipher.encrypt("document".toByteArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 1).toByte()
        val failed = runCatching { cipher.decrypt(encrypted) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `format trop court rejeté`() {
        val failed = runCatching { cipher.decrypt(ByteArray(5)) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `données volumineuses (image simulée)`() {
        val plain = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
        assertEquals(plain.size.toLong(), cipher.decrypt(cipher.encrypt(plain)).size.toLong())
    }
}
