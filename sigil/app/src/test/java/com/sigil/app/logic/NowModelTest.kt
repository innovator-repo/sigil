package com.sigil.app.logic

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowModelTest {
    private fun note(id: String, title: String, updated: Long, pinned: Boolean = false, deleted: Boolean = false, sealed: Boolean = false) =
        NoteItem(
            id = id, space = Space.PERSONAL, pinned = pinned, updated = updated, deletedAt = if (deleted) 1L else null,
            blocks = listOf(
                NoteBlock("b0", BlockType.PARAGRAPH, segments = listOf(Segment.Text(title))),
            ) + if (sealed) listOf(NoteBlock("b1", segments = listOf(Segment.Seal("s1", "PIN")))) else emptyList(),
        )

    private fun login(id: String, deleted: Boolean = false) = LoginItem(
        id = id, space = Space.PERSONAL, updated = 5L, deletedAt = if (deleted) 1L else null,
        title = "Bank", username = "me", passwordSecretId = "s", url = "",
    )

    @Test fun drawerListsPinnedFirstThenRecent() {
        val items = listOf(note("a", "Old", 1), note("b", "New", 9), note("c", "Pinned old", 2, pinned = true), note("d", "Gone", 10, deleted = true))
        assertEquals(listOf("c", "b", "a"), NowModel.drawerNotes(items).map { it.id })
    }

    @Test fun lastNoteSkipsSealedDeletedAndBlank() {
        val items = listOf(
            note("a", "Visible", 5),
            note("b", "Secret stuff", 9, sealed = true),
            note("c", "Trashed", 8, deleted = true),
            note("d", "   ", 7),
        )
        assertEquals("a", NowModel.lastNote(items)?.id)
    }

    @Test fun lastNoteIsNullWithNoData() {
        assertNull(NowModel.lastNote(emptyList()))
        assertNull(NowModel.lastNote(listOf(note("s", "only sealed", 1, sealed = true))))
    }

    @Test fun sealedCountCountsVaultAndSealedNotes() {
        val items = listOf(note("a", "x", 1), note("b", "y", 2, sealed = true), login("l1"), login("l2", deleted = true))
        assertEquals(2, NowModel.sealedCount(items))
        assertEquals(1, NowModel.vaultCount(items))
    }

    @Test fun summaryReadsInPlainWords() {
        assertEquals("No notes yet", NowModel.summary(emptyList()))
        assertEquals("1 note", NowModel.summary(listOf(note("a", "x", 1))))
        assertEquals("2 notes · 1 sealed", NowModel.summary(listOf(note("a", "x", 1), note("b", "y", 2), login("l"))))
    }

    @Test fun isSealedDetectsChips() {
        assertTrue(NowModel.isSealed(note("a", "x", 1, sealed = true)))
        assertFalse(NowModel.isSealed(note("a", "x", 1)))
    }
}
