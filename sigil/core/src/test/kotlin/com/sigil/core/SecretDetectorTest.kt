package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretDetectorTest {

    @Test
    fun detectsLabeledWifiPassword() {
        val text = "Wi-Fi Fernhill-5G, password Maple-harbor-quiet7"
        val hits = SecretDetector.findSecrets(text)
        val hit = hits.find { it.value == "Maple-harbor-quiet7" }
        assertTrue(hit != null, "expected to find the Wi-Fi password in: $hits")
        assertEquals("Wi‑Fi password", hit!!.label)
    }

    @Test
    fun detectsDoorCode() {
        val hits = SecretDetector.findSecrets("Door code 4821#")
        val hit = hits.find { it.value == "4821#" }
        assertTrue(hit != null, "expected a door code match in: $hits")
        assertEquals("Door code", hit!!.label)
    }

    @Test
    fun detectsKnownGithubToken() {
        val token = "ghp_" + "a".repeat(36)
        val hits = SecretDetector.findSecrets("export GITHUB_TOKEN=$token")
        val hit = hits.find { it.value == token }
        assertTrue(hit != null, "expected to find the GitHub token in: $hits")
        assertEquals("GitHub token", hit!!.label)
        assertEquals(0.99, hit.confidence)
    }

    @Test
    fun detectsLuhnValidCardNumber() {
        val hits = SecretDetector.findSecrets("Card: 4242 4242 4242 4242 exp 08/29")
        val hit = hits.find { it.label == "Card number" }
        assertTrue(hit != null, "expected a card number match in: $hits")
        assertEquals("4242 4242 4242 4242", hit!!.value)
    }

    @Test
    fun rejectsLuhnInvalidDigitRun() {
        val hits = SecretDetector.findSecrets("Order number 1234 5678 9012 3456")
        assertTrue(hits.none { it.label == "Card number" }, "1234567890123456 fails luhn, should not be flagged: $hits")
    }

    @Test
    fun ignoresUrlsAndPlainSentences() {
        val hits = SecretDetector.findSecrets(
            "Visit https://example.com/some/long/path for details, or email alex.morgan@gmail.com.",
        )
        assertTrue(hits.isEmpty(), "urls/emails should never be flagged as secrets: $hits")
        val plain = SecretDetector.findSecrets("Let's meet for coffee tomorrow around ten near the office.")
        assertTrue(plain.isEmpty(), "ordinary prose should never be flagged: $plain")
    }

    @Test
    fun detectsHighEntropyGenericToken() {
        val hits = SecretDetector.findSecrets("staging key: Kx9#mQ2vL8pR\$zT4w")
        assertTrue(hits.isNotEmpty(), "expected a high-entropy token match")
    }

    @Test
    fun overlappingMatchesKeepOnlyHighestConfidence() {
        val token = "ghp_" + "b".repeat(36)
        val hits = SecretDetector.findSecrets("token: $token")
        // the KNOWN github-token rule (0.99) and the generic/labeled rules would both
        // match overlapping ranges; only the highest-confidence one should survive.
        assertEquals(1, hits.count { it.value.contains(token) || it.value == token })
    }
}
