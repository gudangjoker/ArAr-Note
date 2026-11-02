package com.gudangjoker.ararnote.security

/**
 * Compatibility wrapper for Base64 encoding/decoding.
 * On Android runtime, uses android.util.Base64 (compatible down to API 24).
 * On standard JVM test runners, gracefully falls back to java.util.Base64.
 */
object Base64Compat {

    fun encodeToString(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun decode(encodedString: String): ByteArray {
        return try {
            android.util.Base64.decode(encodedString, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            java.util.Base64.getDecoder().decode(encodedString.trim())
        }
    }
}
