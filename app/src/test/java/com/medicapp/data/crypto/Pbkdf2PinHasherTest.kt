package com.medicapp.data.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pbkdf2PinHasherTest {

    private val hasher = Pbkdf2PinHasher()

    @Test
    fun `pin correct vérifié`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("1234", salt)
        assertTrue(hasher.verify("1234", salt, hash))
    }

    @Test
    fun `pin incorrect rejeté`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("1234", salt)
        assertFalse(hasher.verify("1235", salt, hash))
        assertFalse(hasher.verify("123", salt, hash))
    }

    @Test
    fun `sels différents produisent des hash différents`() {
        val salt1 = hasher.newSalt()
        val salt2 = hasher.newSalt()
        val hash1 = hasher.hash("1234", salt1)
        val hash2 = hasher.hash("1234", salt2)
        assertFalse(hash1.contentEquals(hash2))
        assertTrue(hasher.verify("1234", salt1, hash1))
        assertTrue(hasher.verify("1234", salt2, hash2))
    }
}
