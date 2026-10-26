package com.gudangjoker.ararnote.security

import android.util.Base64
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100000
    private const val LEGACY_ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16

    fun encrypt(plainText: String, password: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        try {
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Format: salt (16 bytes) + iv (16 bytes) + cipherText
            val combined = ByteArray(salt.size + iv.size + cipherText.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun decrypt(encodedCipherText: String, password: CharArray): String {
        val combined = Base64.decode(encodedCipherText, Base64.NO_WRAP)
        if (combined.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val cipherText = ByteArray(combined.size - SALT_LENGTH - IV_LENGTH)

        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.size)

        // Try standard 100,000 iterations first
        return try {
            decryptWithIterations(salt, iv, cipherText, password, ITERATIONS)
        } catch (e: GeneralSecurityException) {
            // Fallback to legacy 10,000 iterations for existing notes
            decryptWithIterations(salt, iv, cipherText, password, LEGACY_ITERATIONS)
        }
    }

    private fun decryptWithIterations(
        salt: ByteArray,
        iv: ByteArray,
        cipherText: ByteArray,
        password: CharArray,
        iterations: Int
    ): String {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH)
        try {
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(cipherText)
            return String(decryptedBytes, Charsets.UTF_8)
        } finally {
            spec.clearPassword()
        }
    }
}
