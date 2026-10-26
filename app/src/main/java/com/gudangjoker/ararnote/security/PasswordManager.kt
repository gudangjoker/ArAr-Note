package com.gudangjoker.ararnote.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class PasswordManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPasswordSet(): Boolean {
        return prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)
    }

    fun setPassword(password: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val hash = hashPbkdf2(password, salt)
        prefs.edit()
            .putInt(KEY_HASH_VERSION, VERSION_PBKDF2)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()

        // If biometric was enabled, re-encrypt the new password with Keystore
        if (isBiometricEnabled()) {
            storeBiometricPassword(password)
        }
    }

    fun verifyPassword(password: String): Boolean {
        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val expectedHashStr = prefs.getString(KEY_HASH, null) ?: return false

        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expectedHash = Base64.decode(expectedHashStr, Base64.NO_WRAP)

        val version = prefs.getInt(KEY_HASH_VERSION, VERSION_LEGACY_SHA256)
        return if (version == VERSION_LEGACY_SHA256) {
            val computedHash = hashSha256(password, salt)
            val matches = MessageDigest.isEqual(computedHash, expectedHash)
            if (matches) {
                // Seamless transparent migration to PBKDF2 (100k iterations)
                setPassword(password)
                true
            } else {
                false
            }
        } else {
            val computedHash = hashPbkdf2(password, salt)
            MessageDigest.isEqual(computedHash, expectedHash)
        }
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_HASH_VERSION)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_BIOMETRIC_ENCRYPTED_PWD)
            .apply()
        deleteKeystoreKey()
    }

    // Biometric Integration
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && prefs.contains(KEY_BIOMETRIC_ENCRYPTED_PWD)
    }

    fun setBiometricEnabled(enabled: Boolean, currentPassword: String?): Boolean {
        return if (enabled && currentPassword != null) {
            val success = storeBiometricPassword(currentPassword)
            if (success) {
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
            }
            success
        } else {
            prefs.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                .remove(KEY_BIOMETRIC_ENCRYPTED_PWD)
                .apply()
            deleteKeystoreKey()
            true
        }
    }

    fun getBiometricDecryptCryptoObject(): BiometricPrompt.CryptoObject? {
        val encoded = prefs.getString(KEY_BIOMETRIC_ENCRYPTED_PWD, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            null
        }
    }

    fun decryptBiometricPassword(authenticatedCipher: Cipher?): String? {
        if (authenticatedCipher == null) return getBiometricPassword()
        val encoded = prefs.getString(KEY_BIOMETRIC_ENCRYPTED_PWD, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val decryptedBytes = authenticatedCipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun getBiometricEncryptCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            null
        }
    }

    fun saveBiometricPasswordWithCipher(authenticatedCipher: Cipher, password: String): Boolean {
        return try {
            val iv = authenticatedCipher.iv
            val cipherText = authenticatedCipher.doFinal(password.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            prefs.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                .putString(KEY_BIOMETRIC_ENCRYPTED_PWD, Base64.encodeToString(combined, Base64.NO_WRAP))
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getBiometricPassword(): String? {
        val encoded = prefs.getString(KEY_BIOMETRIC_ENCRYPTED_PWD, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun storeBiometricPassword(password: String): Boolean {
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            prefs.edit()
                .putString(KEY_BIOMETRIC_ENCRYPTED_PWD, Base64.encodeToString(combined, Base64.NO_WRAP))
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun deleteKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    private fun hashSha256(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun hashPbkdf2(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val PREF_NAME = "arar_note_security_prefs"
        private const val KEY_SALT = "master_pwd_salt"
        private const val KEY_HASH = "master_pwd_hash"
        private const val KEY_HASH_VERSION = "master_pwd_version"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ENCRYPTED_PWD = "biometric_encrypted_pwd"

        private const val VERSION_LEGACY_SHA256 = 1
        private const val VERSION_PBKDF2 = 2

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "ArArNoteBiometricKey"
        private const val KEYSTORE_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
