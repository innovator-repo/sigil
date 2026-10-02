package com.sigil.core

import java.security.SecureRandom
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log2

/** A freshly generated secret and its estimated entropy in bits. */
data class GeneratedSecret(val value: String, val bits: Double)

/** A human-readable strength summary for a given entropy. */
data class Strength(val label: String, val timeToGuess: String)

enum class CharClass { PLAIN, DIGIT, SYMBOL }

/** One character of a generated password, classified for coloring (digits/symbols get their own color). */
data class ColoredChar(val char: Char, val charClass: CharClass)

enum class GeneratorStyle { STRONG, MEMORABLE, PIN }

/**
 * Password/PIN/passphrase generator. Ported from kept/core/src/generator.js so the
 * same patterns, word list and entropy estimates are used here.
 */
object PasswordGenerator {
    private val rng = SecureRandom()
    private const val CONSONANTS = "bcdfghjkmnpqrstvwxz"
    private const val VOWELS = "aeiouy"
    private const val DIGITS = "23456789"

    private fun randomInt(bound: Int): Int = rng.nextInt(bound)
    private fun pick(s: String): Char = s[randomInt(s.length)]

    fun genStrong(): GeneratedSecret {
        val pattern = "cvccvc".repeat(3)
        val flat = pattern.map { p -> if (p == 'c') pick(CONSONANTS) else pick(VOWELS) }.toMutableList()
        val digitAt = randomInt(flat.size)
        val slots = pattern.indices.filter { i -> pattern[i] == 'c' && i != digitAt }
        val capAt = slots[randomInt(slots.size)]
        flat[digitAt] = pick(DIGITS)
        flat[capAt] = flat[capAt].uppercaseChar()
        val value = listOf(0, 6, 12).joinToString("-") { i -> flat.subList(i, i + 6).joinToString("") }
        val bits = 3 * (4 * log2(19.0) + 2 * log2(6.0)) - log2(19.0) + log2(8.0) + log2(18.0) + log2(11.0)
        return GeneratedSecret(value, bits)
    }

    fun genMemorable(n: Int): GeneratedSecret {
        val words = WordList.WORDS
        val list = MutableList(n) { words[randomInt(words.size)] }
        val c = randomInt(n)
        val d = randomInt(n)
        list[c] = list[c].replaceFirstChar { it.uppercaseChar() }
        list[d] = list[d] + pick(DIGITS)
        val bits = n * 10 + 2 * log2(n.toDouble()) + 3
        return GeneratedSecret(list.joinToString("-"), bits)
    }

    private val allSame = Regex("^(\\d)\\1+$")
    fun genPin(n: Int = 6): GeneratedSecret {
        while (true) {
            val v = (0 until n).joinToString("") { randomInt(10).toString() }
            if (!allSame.matches(v) &&
                !"01234567890123456789".contains(v) &&
                !"98765432109876543210".contains(v)
            ) {
                return GeneratedSecret(v, n * log2(10.0))
            }
        }
    }

    fun generate(style: GeneratorStyle, words: Int = 5, pinLength: Int = 6): GeneratedSecret = when (style) {
        GeneratorStyle.STRONG -> genStrong()
        GeneratorStyle.MEMORABLE -> genMemorable(words)
        GeneratorStyle.PIN -> genPin(pinLength)
    }

    private val UNITS = listOf(
        "second" to 1.0,
        "minute" to 60.0,
        "hour" to 3600.0,
        "day" to 86400.0,
        "year" to 31557600.0,
        "century" to 3155760000.0,
    )
    private val enUs = NumberFormat.getIntegerInstance(Locale.US)

    fun describe(bits: Double): Strength {
        val s = Math.pow(2.0, bits - 1) / 1e10
        val label = when {
            bits < 28 -> "Easy to guess"
            bits < 40 -> "Could be guessed"
            bits < 60 -> "Strong"
            bits < 80 -> "Very strong"
            else -> "Practically unguessable"
        }
        val time: String = when {
            s < 1 -> "instantly"
            s > 3155760000.0 * 1e6 -> "longer than the age of the universe"
            else -> {
                var unit = UNITS[0]
                for (u in UNITS) if (s >= u.second) unit = u
                val n = floor(s / unit.second).toLong()
                val word = when {
                    n == 1L -> unit.first
                    unit.first == "century" -> "centuries"
                    else -> unit.first + "s"
                }
                "${enUs.format(n)} $word"
            }
        }
        return Strength(label, time)
    }

    /** Classifies each character of a generated value for digit/symbol coloring, matching `.pw .d` / `.pw .s`. */
    fun colorize(value: String): List<ColoredChar> = value.map { c ->
        val cls = when {
            c.isDigit() -> CharClass.DIGIT
            !c.isLetterOrDigit() -> CharClass.SYMBOL
            else -> CharClass.PLAIN
        }
        ColoredChar(c, cls)
    }
}
