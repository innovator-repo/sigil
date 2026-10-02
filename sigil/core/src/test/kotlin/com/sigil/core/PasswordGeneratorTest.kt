package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordGeneratorTest {

    @Test
    fun genStrongShapeAndEntropy() {
        repeat(50) {
            val (value, bits) = PasswordGenerator.genStrong()
            val groups = value.split("-")
            assertEquals(3, groups.size, "expected 3 groups, got $value")
            groups.forEach { assertEquals(6, it.length, "group length in $value") }
            val digits = value.count { it.isDigit() }
            assertEquals(1, digits, "exactly one digit in $value")
            val uppers = value.count { it.isUpperCase() }
            assertEquals(1, uppers, "exactly one uppercase letter in $value")
            assertTrue(bits > 50, "strong passwords should be well above 50 bits, got $bits")
        }
    }

    @Test
    fun genMemorableShape() {
        repeat(50) {
            val (value, bits) = PasswordGenerator.genMemorable(5)
            val words = value.split("-")
            assertEquals(5, words.size)
            assertTrue(words.any { it.isNotEmpty() && it[0].isUpperCase() }, "one word should be capitalized: $value")
            assertTrue(words.any { w -> w.isNotEmpty() && w.last().isDigit() }, "one word should end in a digit: $value")
            assertTrue(bits > 0)
        }
    }

    @Test
    fun genPinNeverTrivial() {
        repeat(200) {
            val (value, bits) = PasswordGenerator.genPin(6)
            assertEquals(6, value.length)
            assertTrue(value.all { it.isDigit() })
            assertTrue(!Regex("^(\\d)\\1+$").matches(value), "rejects repeated digits: $value")
            assertTrue(value != "012345" && value != "123456" && value != "654321", "rejects simple runs: $value")
            assertApprox(6 * kotlin.math.log2(10.0), bits)
        }
    }

    @Test
    fun describeBuckets() {
        assertEquals("Easy to guess", PasswordGenerator.describe(10.0).label)
        assertEquals("Could be guessed", PasswordGenerator.describe(35.0).label)
        assertEquals("Strong", PasswordGenerator.describe(50.0).label)
        assertEquals("Very strong", PasswordGenerator.describe(70.0).label)
        assertEquals("Practically unguessable", PasswordGenerator.describe(100.0).label)
        assertEquals("instantly", PasswordGenerator.describe(1.0).timeToGuess)
    }

    @Test
    fun colorizeClassifiesEachCharacter() {
        val colored = PasswordGenerator.colorize("ab1-C2")
        assertEquals(
            listOf(
                CharClass.PLAIN, CharClass.PLAIN, CharClass.DIGIT,
                CharClass.SYMBOL, CharClass.PLAIN, CharClass.DIGIT,
            ),
            colored.map { it.charClass },
        )
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 0.001) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected, got $actual")
    }
}
