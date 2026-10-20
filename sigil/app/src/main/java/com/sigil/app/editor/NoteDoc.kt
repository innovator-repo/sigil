package com.sigil.app.editor

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.Segment

/** Stands in for a sealed chip inside the editor's text. One char per chip, so a chip moves and deletes as a unit. */
const val SEAL_CHAR = '￼'

data class LineMeta(val type: BlockType = BlockType.PARAGRAPH, val done: Boolean = false) {
    val isCheck: Boolean get() = type == BlockType.CHECKLIST
}

data class SealRef(val secretId: String, val label: String)

/**
 * A note as the editor sees it: one string where each line is a block (line 0 is the title)
 * and each [SEAL_CHAR] is a sealed chip, plus per-line checklist state and the chips in order.
 * This is the shape a single text field can edit, so Enter, Backspace, selection and the IME
 * all behave natively; [applyEdit] keeps the line and chip lists in step with the text.
 */
data class NoteDoc(val text: String, val lines: List<LineMeta>, val seals: List<SealRef>) {
    init {
        require(lines.size == text.count { it == '\n' } + 1) { "one LineMeta per line" }
        require(seals.size == text.count { it == SEAL_CHAR }) { "one SealRef per chip" }
    }

    val lineCount: Int get() = lines.size

    fun lineIndexAt(offset: Int): Int {
        var n = 0
        for (i in 0 until offset.coerceAtMost(text.length)) if (text[i] == '\n') n++
        return n
    }

    fun lineStart(index: Int): Int {
        if (index == 0) return 0
        var n = 0
        for (i in text.indices) if (text[i] == '\n' && ++n == index) return i + 1
        return text.length
    }

    fun lineEnd(index: Int): Int = text.indexOf('\n', lineStart(index)).let { if (it == -1) text.length else it }

    fun lineText(index: Int): String = text.substring(lineStart(index), lineEnd(index))

    /** The line with chips blanked out, same length as the raw line, for secret detection. */
    fun lineTextForDetection(index: Int): String = lineText(index).replace(SEAL_CHAR, ' ')

    fun sealsBefore(offset: Int): Int = text.substring(0, offset).count { it == SEAL_CHAR }

    val isBlank: Boolean get() = seals.isEmpty() && text.isBlank()

    /** Anything written below the title line. */
    val hasBody: Boolean get() = seals.isNotEmpty() || text.substringAfter('\n', "").isNotBlank()

    fun toBlocks(): List<NoteBlock> = text.split('\n').mapIndexed { i, line ->
        val segments = ArrayList<Segment>()
        var sealIndex = sealsBefore(lineStart(i))
        val run = StringBuilder()
        for (c in line) {
            if (c == SEAL_CHAR) {
                if (run.isNotEmpty()) segments.add(Segment.Text(run.toString())).also { run.clear() }
                val ref = seals[sealIndex++]
                segments.add(Segment.Seal(ref.secretId, ref.label))
            } else {
                run.append(c)
            }
        }
        if (run.isNotEmpty() || segments.isEmpty()) segments.add(Segment.Text(run.toString()))
        NoteBlock(id = "b$i", type = lines[i].type, done = lines[i].done, segments = segments)
    }

    fun withLine(index: Int, meta: LineMeta): NoteDoc =
        copy(lines = lines.toMutableList().also { it[index] = meta })

    /** Turns a body line into a checklist item or back; the title line never becomes one. */
    fun toggleChecklist(index: Int): NoteDoc {
        if (index == 0) return this
        val m = lines[index]
        return withLine(index, if (m.isCheck) LineMeta() else LineMeta(BlockType.CHECKLIST, false))
    }

    fun toggleDone(index: Int): NoteDoc {
        val m = lines[index]
        return if (m.isCheck) withLine(index, m.copy(done = !m.done)) else this
    }

    /**
     * Replaces [start, end) (within one line) with a chip, adding a space after it unless one
     * is already there. Returns the new doc and where the cursor should go.
     */
    fun seal(start: Int, end: Int, ref: SealRef): Pair<NoteDoc, Int> {
        val k = sealsBefore(start)
        val removedSeals = text.substring(start, end).count { it == SEAL_CHAR }
        val after = text.substring(end)
        val space = if (after.startsWith(" ")) "" else " "
        val newText = text.substring(0, start) + SEAL_CHAR + space + after
        val newSeals = seals.subList(0, k) + ref + seals.subList(k + removedSeals, seals.size)
        return NoteDoc(newText, lines, newSeals) to (start + 2)
    }

    /**
     * Reconciles line and chip state after the text field reports [newText].
     *
     * The edit is located using the new cursor (text was inserted just before it), which
     * keeps checklist state on the right lines when text repeats, e.g. Enter on an empty
     * line next to another empty line. Two edits are turned into prototype behaviors instead:
     * Enter on an empty checklist item ends the list, and Backspace at the start of a
     * checklist item turns it back into a paragraph.
     */
    fun applyEdit(newText: String, oldCursor: Int, newCursor: Int): EditOutcome {
        if (newText == text) return EditOutcome(this, null)
        val oldLen = text.length
        val newLen = newText.length

        var a: Int
        var b: Int
        var c: Int
        val tail = newLen - newCursor
        val bGuess = oldLen - tail
        if (newCursor in 0..newLen && bGuess in 0..oldLen && text.regionMatches(bGuess, newText, newCursor, tail)) {
            b = bGuess
            c = newCursor
            val limit = minOf(c, b)
            a = 0
            while (a < limit && text[a] == newText[a]) a++
        } else {
            val limit = minOf(oldLen, newLen)
            a = 0
            while (a < limit && text[a] == newText[a]) a++
            var s = 0
            while (s < limit - a && text[oldLen - 1 - s] == newText[newLen - 1 - s]) s++
            b = oldLen - s
            c = newLen - s
        }

        val removed = text.substring(a, b)
        var inserted = newText.substring(a, c)
        val lp = lineIndexAt(a)

        if (removed.isEmpty() && inserted == "\n" && lp > 0 && lines[lp].isCheck && lineText(lp).isBlank()) {
            return EditOutcome(withLine(lp, LineMeta()), cursor = a)
        }
        if (inserted.isEmpty() && removed == "\n" && oldCursor == b && lines[lp + 1].isCheck) {
            return EditOutcome(withLine(lp + 1, LineMeta()), cursor = b)
        }

        var cursorOverride: Int? = null
        if (inserted.indexOf(SEAL_CHAR) >= 0) {
            inserted = inserted.replace(SEAL_CHAR.toString(), "")
            cursorOverride = a + inserted.length
        }
        val resultText = text.substring(0, a) + inserted + text.substring(b)

        val kr = removed.count { it == '\n' }
        val ki = inserted.count { it == '\n' }
        val base = lines[lp]
        val continued = if (lp > 0 && base.isCheck) LineMeta(BlockType.CHECKLIST, false) else LineMeta()
        val atLineStart = a == lineStart(lp) && removed.isEmpty() && inserted.isNotEmpty() && inserted.all { it == '\n' }
        val newLines = ArrayList<LineMeta>(lines.size - kr + ki)
        newLines.addAll(lines.subList(0, lp))
        if (atLineStart && lineText(lp).isNotEmpty()) {
            // Enter at the start of a line pushes the line down intact, keeping its own state.
            repeat(ki) { newLines.add(if (lp + it == 0) LineMeta() else continued) }
            newLines.add(base)
        } else {
            newLines.add(base)
            repeat(ki) { newLines.add(continued) }
        }
        newLines.addAll(lines.subList(lp + kr + 1, lines.size))
        if (newLines[0] != LineMeta()) newLines[0] = LineMeta()

        val sa = text.substring(0, a).count { it == SEAL_CHAR }
        val sr = removed.count { it == SEAL_CHAR }
        val newSeals = seals.subList(0, sa) + seals.subList(sa + sr, seals.size)

        return EditOutcome(NoteDoc(resultText, newLines, newSeals), cursorOverride)
    }

    companion object {
        val Empty = NoteDoc("", listOf(LineMeta()), emptyList())

        fun fromBlocks(blocks: List<NoteBlock>): NoteDoc {
            if (blocks.isEmpty()) return Empty
            val sb = StringBuilder()
            val lines = ArrayList<LineMeta>(blocks.size)
            val seals = ArrayList<SealRef>()
            blocks.forEachIndexed { i, block ->
                if (i > 0) sb.append('\n')
                for (s in block.segments) when (s) {
                    is Segment.Text -> sb.append(s.value.replace('\n', ' ').replace(SEAL_CHAR.toString(), ""))
                    is Segment.Seal -> {
                        sb.append(SEAL_CHAR)
                        seals.add(SealRef(s.secretId, s.label))
                    }
                }
                lines.add(if (i == 0) LineMeta() else LineMeta(block.type, block.done && block.type == BlockType.CHECKLIST))
            }
            return NoteDoc(sb.toString(), lines, seals)
        }

        /** A new note seeded with [title] (from the Find bar), cursor ready on the line below. */
        fun withTitle(title: String): NoteDoc =
            if (title.isBlank()) Empty else NoteDoc(title.replace('\n', ' ') + "\n", listOf(LineMeta(), LineMeta()), emptyList())
    }
}

/** [cursor] is non-null when the editor must move the cursor itself (the text field's own position no longer applies). */
data class EditOutcome(val doc: NoteDoc, val cursor: Int?)
