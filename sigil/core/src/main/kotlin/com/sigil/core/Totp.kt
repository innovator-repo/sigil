package com.sigil.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 time-based one-time codes (HMAC-SHA1, 30s step, 6 digits), matching the
 * verification codes shown next to logins with 2FA turned on.
 */
object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(secret: String): ByteArray {
        val clean = secret.uppercase().replace(Regex("[\\s=-]"), "")
        var bits = 0
        var value = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8 + 1)
        for (ch in clean) {
            val idx = ALPHABET.indexOf(ch)
            if (idx < 0) continue
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                out.add(((value shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** Returns the current 6-digit code for `secret`, or null if HMAC-SHA1 isn't available. */
    fun code(secret: String, timeMillis: Long = System.currentTimeMillis()): String? = try {
        val key = base32Decode(secret)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val counter = timeMillis / 30000
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = mac.doFinal(msg)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        (binary % 1_000_000).toString().padStart(6, '0')
    } catch (e: Exception) {
        null
    }

    /** Seconds remaining in the current 30s window, for the countdown ring. */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int =
        30 - ((timeMillis / 1000) % 30).toInt()
}
