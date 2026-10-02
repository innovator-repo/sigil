package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TotpTest {

    /** RFC4648 base32 encoder, written independently of [Totp]'s decoder to cross-check it. */
    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var bits = 0
        var value = 0
        for (b in data) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(alphabet[(value shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(alphabet[(value shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    // RFC 6238 Appendix B published test vectors (HMAC-SHA1, shared secret "12345678901234567890" ASCII).
    private val rfcSecret = base32Encode("12345678901234567890".toByteArray(Charsets.US_ASCII))

    @Test
    fun matchesRfc6238TestVectors() {
        // RFC vector: Time=59 -> TOTP 94287082 (we truncate to the last 6 digits, as the app does)
        assertEquals("287082", Totp.code(rfcSecret, 59_000L))
        // RFC vector: Time=1111111109 -> TOTP 07081804
        assertEquals("081804", Totp.code(rfcSecret, 1_111_111_109_000L))
        // RFC vector: Time=1111111111 -> TOTP 14050471
        assertEquals("050471", Totp.code(rfcSecret, 1_111_111_111_000L))
    }

    @Test
    fun codeIsDeterministicWithinAWindow() {
        val a = Totp.code(rfcSecret, 1_000_000_000_000L)
        val b = Totp.code(rfcSecret, 1_000_000_000_010L)
        assertEquals(a, b)
    }

    @Test
    fun codeChangesAcrossWindows() {
        val a = Totp.code(rfcSecret, 0L)
        val b = Totp.code(rfcSecret, 10 * 30_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun codeIsAlwaysSixDigits() {
        repeat(20) { i ->
            val code = Totp.code(rfcSecret, i * 37_123_456L)
            assertTrue(code != null && code.length == 6 && code.all { it.isDigit() }, "got $code")
        }
    }

    @Test
    fun secondsRemainingStaysInRange() {
        for (t in 0L until 90_000L step 1000) {
            val s = Totp.secondsRemaining(t)
            assertTrue(s in 1..30, "t=$t -> $s")
        }
    }
}
