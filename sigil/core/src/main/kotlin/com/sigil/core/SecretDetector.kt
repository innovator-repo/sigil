package com.sigil.core

import kotlin.math.ln

/** A span of text in a note that looks like a secret worth sealing. */
data class SecretMatch(val start: Int, val value: String, val label: String, val confidence: Double) {
    val end: Int get() = start + value.length
}

/**
 * Detects likely secrets (passwords, Wi‑Fi keys, API tokens, card numbers, door codes...)
 * inside free-form note text, so Sigil can offer to seal them the moment they're typed.
 * Ported rule-for-rule from kept/core/src/detect.js.
 */
object SecretDetector {

    private val known: List<Pair<Regex, String>> = listOf(
        Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\\b") to "GitHub token",
        Regex("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b") to "AWS key",
        Regex("\\b[sr]k_(?:live|test)_[A-Za-z0-9]{16,}\\b") to "API key",
        Regex("\\bxox[abprs]-[A-Za-z0-9-]{10,}\\b") to "Slack token",
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b") to "API key",
    )

    private val labels: List<Pair<Regex, String>> = listOf(
        Regex("wi-?fi|wlan|network", RegexOption.IGNORE_CASE) to "Wi\u2011Fi password",
        Regex("door|gate|garage|lockbox|alarm", RegexOption.IGNORE_CASE) to "Door code",
        Regex("\\bpin\\b", RegexOption.IGNORE_CASE) to "PIN",
        Regex("api|token|secret|key", RegexOption.IGNORE_CASE) to "Key",
    )

    private val labeled = Regex(
        "\\b(wi-?fi\\s+(?:password|pw|pass)|password|passcode|passwort|pwd|pw|pin|" +
            "(?:door|gate|alarm|entry|lockbox)\\s+code|api\\s+key|secret|token)\\b" +
            "(\\s*(?:is\\s+|[:=\\-\u2013\u2014]\\s*)|\\s+)(\\S{4,})",
        RegexOption.IGNORE_CASE,
    )

    private val cardCandidate = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val genericToken = Regex("\\S{10,128}")
    private val sepOrIs = Regex("[:=\\-\u2013\u2014]|is")
    private val codeShape = Regex("^[\\d#*]{4,10}$")
    private val keyKw = Regex("^(api key|secret|token)$")
    private val digitOrSymbol = Regex("[\\d\\W]")
    private val urlPrefix = Regex("^(https?://|www\\.)", RegexOption.IGNORE_CASE)
    private val emailish = Regex("^[^\\s@]+@[^\\s@]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE)
    private val domainish = Regex("^[\\w-]+(\\.[\\w-]+)+/?$")
    private val timeOfDay = Regex("^\\d{1,2}[:.]\\d{2}([:.]\\d{2})?(am|pm)?$", RegexOption.IGNORE_CASE)
    private val dateish = Regex("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$")
    private val pathPrefix = Regex("^(~|\\.{1,2})?/")
    private val leading = Regex("^[(\"'\u201c\u2018\\[]+")
    private val trailing = Regex("[)\"'\u201d\u2019\\].,;!?]+$")

    private fun classes(s: String): Int {
        var n = 0
        if (s.any { it in 'a'..'z' }) n++
        if (s.any { it in 'A'..'Z' }) n++
        if (s.any { it.isDigit() }) n++
        if (s.any { !it.isLetterOrDigit() }) n++
        return n
    }

    private fun entropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val freq = HashMap<Char, Int>()
        for (c in s) freq[c] = (freq[c] ?: 0) + 1
        var h = 0.0
        for (count in freq.values) {
            val p = count.toDouble() / s.length
            h -= p * (ln(p) / ln(2.0))
        }
        return h
    }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        var dbl = false
        for (i in digits.length - 1 downTo 0) {
            var x = digits[i] - '0'
            if (dbl) {
                x *= 2
                if (x > 9) x -= 9
            }
            sum += x
            dbl = !dbl
        }
        return sum % 10 == 0
    }

    private fun urlish(s: String): Boolean =
        urlPrefix.containsMatchIn(s) || emailish.matches(s) || domainish.matches(s)

    private fun timeish(s: String): Boolean = timeOfDay.matches(s) || dateish.matches(s)

    private fun pathish(s: String): Boolean =
        pathPrefix.containsMatchIn(s) || s.count { it == '/' } >= 2

    private fun trimP(s: String): String = s.replace(leading, "").replace(trailing, "")

    private fun labelAt(text: String, index: Int): String {
        val lineStart = if (index <= 0) 0 else text.lastIndexOf('\n', index - 1) + 1
        val before = text.substring(lineStart.coerceIn(0, text.length), index.coerceIn(lineStart, text.length))
        var best: String? = null
        var at = -1
        for ((re, label) in labels) {
            for (m in re.findAll(before)) {
                if (m.range.first > at) {
                    best = label
                    at = m.range.first
                }
            }
        }
        return best ?: "Password"
    }

    /** Finds every secret-looking span in `text`, highest confidence first, with overlaps resolved. */
    fun findSecrets(text: String): List<SecretMatch> {
        val found = ArrayList<SecretMatch>()

        for ((re, label) in known) {
            for (m in re.findAll(text)) {
                found.add(SecretMatch(m.range.first, m.value, label, 0.99))
            }
        }

        for (m in cardCandidate.findAll(text)) {
            val raw = m.value.trimEnd().let { if (it.isNotEmpty() && (it.last() == ' ' || it.last() == '-')) it.dropLast(1) else it }
            val digits = raw.filter { it.isDigit() }
            if (digits.length in 13..19 && luhn(digits)) {
                found.add(SecretMatch(m.range.first, raw, "Card number", 0.95))
            }
        }

        for (m in labeled.findAll(text)) {
            val kw = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
            val sepGroup = m.groupValues[2]
            val sep = sepOrIs.containsMatchIn(sepGroup)
            val rawValue = m.groupValues[3]
            val value = trimP(rawValue)
            if (value.length < 4 || urlish(value) || timeish(value)) continue
            val isCode = kw == "pin" || kw.endsWith(" code")
            val isKey = keyKw.matches(kw)
            val ok = when {
                isCode -> codeShape.matches(value)
                isKey -> sep && value.length >= 8 && classes(value) >= 2
                else -> sep || digitOrSymbol.containsMatchIn(value)
            }
            if (!ok) continue
            val wholeStart = m.range.first
            val groupStartInWhole = m.value.length - rawValue.length
            val start = wholeStart + groupStartInWhole + rawValue.indexOf(value)
            found.add(SecretMatch(start, value, labelAt(text, start), 0.9))
        }

        for (m in genericToken.findAll(text)) {
            val value = trimP(m.value)
            if (value.length < 10 || urlish(value) || timeish(value) || pathish(value)) continue
            val k = classes(value)
            if (k >= 3 && entropy(value) >= 3.0) {
                val start = m.range.first + m.value.indexOf(value)
                val c = if (k == 4) 0.8 else 0.65
                found.add(SecretMatch(start, value, labelAt(text, start), c))
            }
        }

        found.sortWith(compareByDescending<SecretMatch> { it.confidence }.thenBy { it.start })
        val kept = ArrayList<SecretMatch>()
        for (f in found) {
            val overlaps = kept.any { k -> f.start < k.end && k.start < f.end }
            if (!overlaps) kept.add(f)
        }
        return kept.sortedBy { it.start }
    }
}
