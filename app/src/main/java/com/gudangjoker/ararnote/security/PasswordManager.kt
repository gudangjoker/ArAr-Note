package com.gudangjoker.ararnote.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PasswordManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPasswordSet(): Boolean {
        return prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)
    }

    fun setPassword(password: String) {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)

        val hash = hashPassword(password, salt)
        prefs.edit()
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
        val computedHash = hashPassword(password, salt)
        val computedHashStr = Base64.encodeToString(computedHash, Base64.NO_WRAP)

        return computedHashStr == expectedHashStr
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_BIOMETRIC_ENCRYPTED_PWD)
            .apply()
    }

    // Biometric Integration
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && prefs.contains(KEY_BIOMETRIC_ENCRYPTED_PWD)
    }

    fun setBiometricEnabled(enabled: Boolean, currentPassword: String?) {
        if (enabled && currentPassword != null) {
            val success = storeBiometricPassword(currentPassword)
            if (success) {
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
            }
        } else {
            prefs.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                .remove(KEY_BIOMETRIC_ENCRYPTED_PWD)
                .apply()
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
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keySpec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val PREF_NAME = "arar_note_security_prefs"
        private const val KEY_SALT = "master_pwd_salt"
        private const val KEY_HASH = "master_pwd_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ENCRYPTED_PWD = "biometric_encrypted_pwd"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "ArArNoteBiometricKey"
        private const val KEYSTORE_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
