package com.gudangjoker.ararnote.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PasswordManager(context: Context) {

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
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
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
    }
}
