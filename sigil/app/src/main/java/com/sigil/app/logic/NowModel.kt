package com.sigil.app.logic

import com.sigil.app.data.model.Item
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.isPassword

/** Pure decisions behind the Now-first home screen and the status provider; no Android types. */
object NowModel {
    /** A note is sealed when any of its lines holds a sealed chip. Its title is never shown outside the app. */
    fun isSealed(note: NoteItem): Boolean = note.blocks.any { b -> b.segments.any { it is Segment.Seal } }

    fun liveNotes(items: List<Item>): List<NoteItem> = items.filterIsInstance<NoteItem>().filter { !it.isDeleted }

    /** The drawer's list: pinned notes first, then the rest, each newest first. */
    fun drawerNotes(items: List<Item>): List<NoteItem> {
        val (pinned, rest) = liveNotes(items).sortedByDescending { it.updated }.partition { it.pinned }
        return pinned + rest
    }

    /** The most recently edited note that has something written in it and no sealed chip. */
    fun lastNote(items: List<Item>): NoteItem? =
        liveNotes(items).filter { n -> !isSealed(n) && n.blocks.any { !it.isBlank } }.maxByOrNull { it.updated }

    /** Items that hold sealed values: everything in the vault, plus notes with a sealed chip. */
    fun sealedCount(items: List<Item>): Int = items.count { !it.isDeleted && (it.isPassword() || (it is NoteItem && isSealed(it))) }

    fun vaultCount(items: List<Item>): Int = items.count { !it.isDeleted && it.isPassword() }

    fun deletedCount(items: List<Item>): Int = items.count { it.isDeleted }

    /** "12 notes · 3 sealed", or "No notes yet". */
    fun summary(items: List<Item>): String {
        val n = liveNotes(items).size
        if (n == 0) return "No notes yet"
        val sealed = sealedCount(items)
        val notes = if (n == 1) "1 note" else "$n notes"
        return if (sealed > 0) "$notes · $sealed sealed" else notes
    }
}

/**
 * What `content://com.sigil.app.provider/status` answers: one row of numbers and, for
 * [lastTitle], the title of the latest edited note that is not sealed. Nothing else leaves.
 */
data class SigilStatus(
    val noteCount: Int,
    val sealedCount: Int,
    val clipArmed: Boolean,
    val lastTitle: String,
    val lastEditedAt: Long,
) {
    fun toRow(): Array<Any> = arrayOf(noteCount, sealedCount, if (clipArmed) 1 else 0, lastTitle, lastEditedAt)

    companion object {
        val COLUMNS = arrayOf("noteCount", "sealedCount", "clipArmed", "lastTitle", "lastEditedAt")
        val EMPTY = SigilStatus(0, 0, false, "", 0L)

        fun compute(items: List<Item>, clipArmed: Boolean): SigilStatus {
            val last = NowModel.lastNote(items)
            return SigilStatus(
                noteCount = NowModel.liveNotes(items).size,
                sealedCount = NowModel.sealedCount(items),
                clipArmed = clipArmed,
                lastTitle = last?.title.orEmpty(),
                lastEditedAt = last?.updated ?: 0L,
            )
        }
    }
}
