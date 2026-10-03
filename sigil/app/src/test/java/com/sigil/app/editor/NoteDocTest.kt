package com.sigil.app.editor

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocTest {
    private val P = LineMeta()
    private val C = LineMeta(BlockType.CHECKLIST, false)
    private val D = LineMeta(BlockType.CHECKLIST, true)

    private fun doc(text: String, vararg lines: LineMeta, seals: List<SealRef> = emptyList()) =
        NoteDoc(text, lines.toList(), seals)

    @Test fun blocksRoundTrip() {
        val blocks = listOf(
            NoteBlock("x", segments = listOf(Segment.Text("Lake house"))),
            NoteBlock("y", segments = listOf(Segment.Text("Door code "), Segment.Seal("s1", "Door code"), Segment.Text(" ok"))),
            NoteBlock("z", type = BlockType.CHECKLIST, done = true, segments = listOf(Segment.Text("Firewood"))),
        )
        val d = NoteDoc.fromBlocks(blocks)
        assertEquals("Lake house\nDoor code $SEAL_CHAR ok\nFirewood", d.text)
        assertEquals(listOf(P, P, D), d.lines)
        val back = d.toBlocks()
        assertEquals(blocks.map { it.segments }, back.map { it.segments })
        assertEquals(blocks.map { it.type to it.done }, back.map { it.type to it.done })
    }

    @Test fun typingKeepsLineState() {
        val d = doc("T\nMilk", P, D)
        val out = d.applyEdit("T\nMilks", oldCursor = 6, newCursor = 7)
        assertEquals(listOf(P, D), out.doc.lines)
        assertNull(out.cursor)
    }

    @Test fun enterInChecklistContinuesIt() {
        val d = doc("T\nMilk", P, D)
        val out = d.applyEdit("T\nMilk\n", oldCursor = 6, newCursor = 7)
        assertEquals("T\nMilk\n", out.doc.text)
        assertEquals(listOf(P, D, C), out.doc.lines)
    }

    @Test fun enterOnEmptyChecklistItemEndsTheList() {
        val d = doc("T\nMilk\n", P, C, C)
        val out = d.applyEdit("T\nMilk\n\n", oldCursor = 7, newCursor = 8)
        assertEquals("T\nMilk\n", out.doc.text)
        assertEquals(listOf(P, C, P), out.doc.lines)
        assertEquals(7, out.cursor)
    }

    @Test fun backspaceAtChecklistStartMakesParagraph() {
        val d = doc("T\nMilk", P, C)
        val out = d.applyEdit("TMilk", oldCursor = 2, newCursor = 1)
        assertEquals("T\nMilk", out.doc.text)
        assertEquals(listOf(P, P), out.doc.lines)
        assertEquals(2, out.cursor)
    }

    @Test fun backspaceAtParagraphStartMerges() {
        val d = doc("T\nOne\nTwo", P, P, P)
        val out = d.applyEdit("T\nOneTwo", oldCursor = 6, newCursor = 5)
        assertEquals(listOf(P, P), out.doc.lines)
    }

    @Test fun enterNextToEmptyLineKeepsStateOnTheRightLine() {
        // Enter at the end of the empty line 1; line 2 is a done checklist item and must stay so.
        val d = doc("x\n\ny", P, P, D)
        val out = d.applyEdit("x\n\n\ny", oldCursor = 2, newCursor = 3)
        assertEquals(listOf(P, P, P, D), out.doc.lines)
    }

    @Test fun enterAtStartOfItemPushesItDownIntact() {
        val d = doc("T\nMilk", P, D)
        val out = d.applyEdit("T\n\nMilk", oldCursor = 2, newCursor = 3)
        assertEquals(listOf(P, C, D), out.doc.lines)
    }

    @Test fun deletingAChipDropsItsRef() {
        val s = listOf(SealRef("a", "Key"), SealRef("b", "PIN"))
        val d = doc("T\nk $SEAL_CHAR p $SEAL_CHAR", P, P, seals = s)
        val out = d.applyEdit("T\nk  p $SEAL_CHAR", oldCursor = 5, newCursor = 4)
        assertEquals(listOf(SealRef("b", "PIN")), out.doc.seals)
    }

    @Test fun pastedObjectCharsAreStripped() {
        val d = doc("T", P)
        val out = d.applyEdit("Tx${SEAL_CHAR}y", oldCursor = 1, newCursor = 4)
        assertEquals("Txy", out.doc.text)
        assertEquals(3, out.cursor)
        assertTrue(out.doc.seals.isEmpty())
    }

    @Test fun titleLineIsNeverAChecklist() {
        val d = doc("T\nMilk", P, D)
        val out = d.applyEdit("Milk", oldCursor = 0, newCursor = 0)
        assertEquals(listOf(P), out.doc.lines)
    }

    @Test fun sealReplacesRangeAndAddsSpace() {
        val d = doc("T\npassword hunter22", P, P)
        val (sealed, cursor) = d.seal(11, 19, SealRef("s", "Password"))
        assertEquals("T\npassword $SEAL_CHAR ", sealed.text)
        assertEquals(listOf(SealRef("s", "Password")), sealed.seals)
        assertEquals(13, cursor)
    }

    @Test fun withTitleStartsOnTheNextLine() {
        val d = NoteDoc.withTitle("Groceries")
        assertEquals("Groceries\n", d.text)
        assertEquals(2, d.lineCount)
    }
}
