package com.gudangjoker.ararnote.backup

import com.gudangjoker.ararnote.data.NoteEntity
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object LegacyBackupParser {

    private val NOTE_MAGIC = byteArrayOf(0x00, 0x4E, 0x00, 0x4F, 0x00, 0x54, 0x00, 0x45)
    // Fixed salt required for legacy encrypted backup compatibility
    private val LEGACY_SALT = "ColorNote Fixed Salt".toByteArray(Charsets.UTF_8)

    /**
     * Checks whether the given byte array begins with the "NOTE" (UTF-16BE) magic header.
     */
    fun isLegacyEncryptedBackup(header: ByteArray): Boolean {
        if (header.size < 8) return false
        for (i in 0 until 8) {
            if (header[i] != NOTE_MAGIC[i]) return false
        }
        return true
    }

    /**
     * Derives AES-128 key and IV using OpenSSL EVP_BytesToKey (MD5, 1 iteration)
     * as used by legacy PBEWITHMD5AND128BITAES-CBC-OPENSSL cipher.
     */
    fun deriveKeyAndIv(password: String): Pair<SecretKeySpec, IvParameterSpec> {
        val passBytes = password.toByteArray(Charsets.UTF_8)
        val md5 = MessageDigest.getInstance("MD5")

        // First hash: key = MD5(password + salt)
        md5.update(passBytes)
        md5.update(LEGACY_SALT)
        val keyBytes = md5.digest()

        // Second hash: iv = MD5(key + password + salt)
        md5.reset()
        md5.update(keyBytes)
        md5.update(passBytes)
        md5.update(LEGACY_SALT)
        val ivBytes = md5.digest()

        return Pair(SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
    }

    /**
     * Decrypts and parses a legacy binary backup file into a list of NoteEntity objects.
     */
    fun parseLegacyBackup(
        fileBytes: ByteArray,
        password: String = "0000"
    ): List<NoteEntity> {
        if (!isLegacyEncryptedBackup(fileBytes)) {
            throw IllegalArgumentException("Not a valid encrypted backup file (missing magic header)")
        }

        val (keySpec, ivSpec) = deriveKeyAndIv(password)

        // Legacy format v2+ files start encrypted payload at offset 28; older v1 files at offset 16.
        val candidateOffsets = listOf(28, 16)
        var decryptedBytes: ByteArray? = null

        for (offset in candidateOffsets) {
            if (offset >= fileBytes.size) continue
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val encryptedLength = fileBytes.size - offset
                decryptedBytes = cipher.doFinal(fileBytes, offset, encryptedLength)
                break
            } catch (e: Exception) {
                // Try next candidate offset
            }
        }

        if (decryptedBytes == null) {
            throw IllegalArgumentException("Failed to decrypt backup with password: $password")
        }

        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        return extractNotesFromDecryptedText(decryptedText)
    }

    /**
     * Extracts NoteEntity records from the decrypted UTF-8 string payload.
     */
    private fun extractNotesFromDecryptedText(decryptedText: String): List<NoteEntity> {
        val restoredNotes = mutableListOf<NoteEntity>()
        val placeholder = "---JSON-RECORD-SEPARATOR---"
        val cleaned = decryptedText.replace(Regex("(?s)\\u0000\\u0000.."), placeholder)
        val records = cleaned.split(placeholder)

        for (record in records) {
            val trimmed = record.trim()
            val firstBrace = trimmed.indexOf('{')
            val lastBrace = trimmed.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                val jsonStr = trimmed.substring(firstBrace, lastBrace + 1)
                try {
                    val obj = JSONObject(jsonStr)
                    // Identify note objects by presence of standard note columns
                    if (obj.has("_id") || obj.has("created_date") || (obj.has("title") && obj.has("note"))) {
                        val note = NoteEntity(
                            id = 0L, // Auto-generate new primary key in Room to preserve all existing notes
                            activeState = obj.optInt("active_state", 0),
                            accountId = obj.optInt("account_id", 0),
                            folderId = obj.optInt("folder_id", 0),
                            status = obj.optInt("status", 0),
                            space = obj.optInt("space", 0),
                            type = obj.optInt("type", 0),
                            title = obj.optString("title", ""),
                            note = obj.optString("note", ""),
                            noteExt = obj.optString("note_ext", ""),
                            noteType = obj.optInt("note_type", 0),
                            tags = obj.optString("tags", ""),
                            importance = obj.optInt("importance", 0),
                            createdDate = obj.optLong("created_date", System.currentTimeMillis()),
                            modifiedDate = obj.optLong("modified_date", System.currentTimeMillis()),
                            reminderType = obj.optInt("reminder_type", 0),
                            reminderDate = obj.optLong("reminder_date", 0L),
                            reminderRepeat = obj.optInt("reminder_repeat", 0),
                            colorIndex = obj.optInt("color_index", 0),
                            encrypted = obj.optInt("encrypted", 0),
                            uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString())
                        )
                        restoredNotes.add(note)
                    }
                } catch (e: Exception) {
                    // Skip non-note objects
                }
            }
        }
        return restoredNotes
    }
}
