package com.medicapp.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCryptoTest {

    @Test
    fun `aller-retour chiffrement par mot de passe`() {
        val plain = "archive de sauvegarde".toByteArray()
        val encrypted = PasswordCrypto.encrypt("motdepasse-super", plain)
        assertFalse(encrypted.contentEquals(plain))
        assertArrayEquals(plain, PasswordCrypto.decrypt("motdepasse-super", encrypted))
    }

    @Test
    fun `mot de passe incorrect rejeté`() {
        val encrypted = PasswordCrypto.encrypt("motdepasse-super", "données".toByteArray())
        val failed = runCatching { PasswordCrypto.decrypt("mauvais", encrypted) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `archive tronquée rejetée`() {
        val encrypted = PasswordCrypto.encrypt("motdepasse", "données".toByteArray())
        val failed = runCatching {
            PasswordCrypto.decrypt("motdepasse", encrypted.copyOf(10))
        }.isFailure
        assertTrue(failed)
    }
}
