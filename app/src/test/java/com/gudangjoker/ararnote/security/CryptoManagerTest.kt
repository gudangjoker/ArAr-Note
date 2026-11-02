package com.gudangjoker.ararnote.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class CryptoManagerTest {

    @Test
    fun testEncryptDecryptRoundTrip() {
        val originalText = "Hello ArAr Note! Catatan rahasia dengan simbol @#$%^&* dan emoji 🔒"
        val password = "StrongPassword2026".toCharArray()

        val encrypted = CryptoManager.encrypt(originalText, password)
        assertNotEquals(originalText, encrypted)
        assertTrue(encrypted.isNotBlank())

        val decrypted = CryptoManager.decrypt(encrypted, password)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testDecryptWithWrongPasswordFails() {
        val originalText = "Sensitive secret data"
        val correctPassword = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()

        val encrypted = CryptoManager.encrypt(originalText, correctPassword)

        try {
            CryptoManager.decrypt(encrypted, wrongPassword)
            fail("Expected exception when decrypting with incorrect password")
        } catch (e: Exception) {
            // Expected BadPaddingException / AEADBadTagException / GeneralSecurityException
            assertTrue(e is GeneralSecurityException || e is IllegalArgumentException)
        }
    }

    @Test
    fun testEmptyStringEncryption() {
        val originalText = ""
        val password = "MyPassword".toCharArray()

        val encrypted = CryptoManager.encrypt(originalText, password)
        val decrypted = CryptoManager.decrypt(encrypted, password)

        assertEquals(originalText, decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDecryptInvalidPayloadSizeThrows() {
        val invalidPayload = Base64Compat.encodeToString(ByteArray(10)) // Less than 32 bytes (salt+iv)
        CryptoManager.decrypt(invalidPayload, "Password".toCharArray())
    }
}
