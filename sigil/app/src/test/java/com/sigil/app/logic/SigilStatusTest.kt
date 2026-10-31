package com.sigil.app.logic

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.Space
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SigilStatusTest {
    private fun note(id: String, title: String, updated: Long, sealed: Boolean = false, deleted: Boolean = false) = NoteItem(
        id = id, space = Space.PERSONAL, updated = updated, deletedAt = if (deleted) 1L else null,
        blocks = listOf(NoteBlock("b0", BlockType.PARAGRAPH, segments = listOf(Segment.Text(title)))) +
            if (sealed) listOf(NoteBlock("b1", segments = listOf(Segment.Seal("s", "Code")))) else emptyList(),
    )

    @Test fun emptyDatabaseGivesZeroRow() {
        val s = SigilStatus.compute(emptyList(), clipArmed = false)
        assertEquals(SigilStatus.EMPTY, s)
        assertArrayEquals(arrayOf<Any>(0, 0, 0, "", 0L), s.toRow())
    }

    @Test fun lastTitleNeverComesFromASealedNote() {
        val items = listOf(note("a", "Groceries", 100), note("b", "Wi-Fi codes", 200, sealed = true))
        val s = SigilStatus.compute(items, clipArmed = true)
        assertEquals("Groceries", s.lastTitle)
        assertEquals(100L, s.lastEditedAt)
        assertFalse(s.toRow().any { it == "Wi-Fi codes" })
        assertEquals(2, s.noteCount)
        assertEquals(1, s.sealedCount)
    }

    @Test fun rowMatchesColumnOrderAndClipFlag() {
        val login = LoginItem(id = "l", space = Space.PERSONAL, updated = 1, title = "Bank", username = "u", passwordSecretId = "s", url = "")
        val s = SigilStatus.compute(listOf(note("a", "Plan", 50), login), clipArmed = true)
        assertArrayEquals(arrayOf("noteCount", "sealedCount", "clipArmed", "lastTitle", "lastEditedAt"), SigilStatus.COLUMNS)
        assertArrayEquals(arrayOf<Any>(1, 1, 1, "Plan", 50L), s.toRow())
    }

    @Test fun deletedNotesDoNotCount() {
        val s = SigilStatus.compute(listOf(note("a", "Trash", 9, deleted = true)), clipArmed = false)
        assertEquals(0, s.noteCount)
        assertEquals("", s.lastTitle)
    }
}
