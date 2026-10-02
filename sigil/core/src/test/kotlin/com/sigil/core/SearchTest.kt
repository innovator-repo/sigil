package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {

    private val docs = listOf(
        SearchDoc("l2", "GitHub", "alexmorgan github.com personal", pinned = false, updated = 300),
        SearchDoc("n2", "Project Aurora launch notes", "launch window moved to tuesday staging api key", pinned = false, updated = 500),
        SearchDoc("w1", "Home Wi-Fi", "Morgan-Home WPA3 Personal family", pinned = true, updated = 100),
        SearchDoc("n5", "Reading: The Extended Mind", "the notebook plays for otto the same role", pinned = false, updated = 50),
    )

    @Test
    fun emptyQueryReturnsMostRecentlyUpdated() {
        val results = Search.search(docs, "")
        assertEquals(docs.sortedByDescending { it.updated }.map { it.id }, results.map { it.id })
    }

    @Test
    fun prefixMatchOnTitleOutranksBodyOnlyMatch() {
        // "proj" only prefixes a title; nothing else should qualify.
        val results = Search.search(docs, "proj")
        assertEquals(listOf("n2"), results.map { it.id })
    }

    @Test
    fun deHyphenatedTokensStillMatch() {
        // "wifi" (no hyphen) should still find "Wi-Fi".
        val results = Search.search(docs, "wifi")
        assertTrue(results.any { it.id == "w1" }, "expected Home Wi-Fi to match 'wifi': $results")
    }

    @Test
    fun everyQueryWordMustMatchTheSameDocument() {
        // "personal" alone also appears in the Wi-Fi item's body, but only the GitHub
        // item's body contains both "github" AND "personal" - the Wi-Fi item must be excluded.
        val results = Search.search(docs, "github personal")
        assertEquals(listOf("l2"), results.map { it.id })
    }

    @Test
    fun pinnedItemsGetATieBreakBoost() {
        val a = SearchDoc("a", "Alpha", "alpha body", pinned = false, updated = 10)
        val b = SearchDoc("b", "Alpha", "alpha body", pinned = true, updated = 10)
        val results = Search.search(listOf(a, b), "alpha")
        assertEquals("b", results.first().id, "pinned item with equal score should sort first")
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(Search.search(docs, "xyznonexistent").isEmpty())
    }
}
