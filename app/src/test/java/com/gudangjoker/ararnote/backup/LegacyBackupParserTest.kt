package com.gudangjoker.ararnote.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBackupParserTest {

    @Test
    fun testIsLegacyEncryptedBackupValidHeader() {
        // UTF-16BE bytes for "NOTE"
        val validHeader = byteArrayOf(0x00, 0x4E, 0x00, 0x4F, 0x00, 0x54, 0x00, 0x45, 0x01, 0x02)
        assertTrue(LegacyBackupParser.isLegacyEncryptedBackup(validHeader))
    }

    @Test
    fun testIsLegacyEncryptedBackupInvalidHeader() {
        val invalidHeader = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        assertFalse(LegacyBackupParser.isLegacyEncryptedBackup(invalidHeader))

        val shortHeader = byteArrayOf(0x00, 0x4E)
        assertFalse(LegacyBackupParser.isLegacyEncryptedBackup(shortHeader))
    }

    @Test
    fun testDeriveKeyAndIv() {
        val (keySpec, ivSpec) = LegacyBackupParser.deriveKeyAndIv("0000")
        assertNotNull(keySpec)
        assertNotNull(ivSpec)
        // AES-128 key length is 16 bytes, IV length is 16 bytes
        assertTrue(keySpec.encoded.size == 16)
        assertTrue(ivSpec.iv.size == 16)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseNonLegacyBackupThrows() {
        val randomBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x7F)
        LegacyBackupParser.parseLegacyBackup(randomBytes, "0000")
    }
}
