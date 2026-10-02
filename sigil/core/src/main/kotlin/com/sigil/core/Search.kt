package com.sigil.core

import java.text.Normalizer

/** A searchable projection of an item, independent of the app's actual data model. */
data class SearchDoc(
    val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val updated: Long,
)

/**
 * Powers the "Find or keep" bar: every query word must prefix-match some token in the
 * document (title, note text, username, url, space...); title hits score higher, and
 * pinned items get a small boost. Ported from the ⌘K bar's `draw()` in kept's app.js.
 */
object Search {
    private val splitter = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{M}")
    private val hyphens = Regex("[-‐‑]")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD).replace(marks, "").lowercase()

    private fun tokens(s: String): List<String> = s.split(splitter).filter { it.isNotEmpty() }

    /** Tokens for a document: plain split, plus a de-hyphenated variant (so "Wi-Fi" also matches "wifi"). */
    private fun docTokens(normalizedBody: String): List<String> =
        tokens(normalizedBody) + tokens(normalizedBody.replace(hyphens, ""))

    fun queryTokens(raw: String): List<String> = tokens(normalize(raw))

    data class Scored(val doc: SearchDoc, val score: Double)

    /** Null if the document doesn't match every query token; otherwise a relevance score. */
    fun score(doc: SearchDoc, query: List<String>): Double? {
        if (query.isEmpty()) return 0.0
        // The title is itself searchable text (not just a scoring boost) - "wifi" must
        // still find an item titled "Home Wi-Fi" even with an empty body.
        val words = docTokens(normalize("${doc.title} ${doc.body}"))
        val titleTokens = tokens(normalize(doc.title))
        if (!query.all { q -> words.any { it.startsWith(q) } }) return null
        var s = 0.0
        for (q in query) s += if (titleTokens.any { it.startsWith(q) }) 3.0 else 1.0
        if (doc.pinned) s += 0.5
        return s
    }

    /** Ranks `docs` against `rawQuery`; empty query returns the 4 most recently updated. */
    fun search(docs: List<SearchDoc>, rawQuery: String, limit: Int = 9): List<SearchDoc> {
        val q = queryTokens(rawQuery)
        if (q.isEmpty()) return docs.sortedByDescending { it.updated }.take(4)
        return docs.mapNotNull { d -> score(d, q)?.let { Scored(d, it) } }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.doc.updated })
            .take(limit)
            .map { it.doc }
    }
}
