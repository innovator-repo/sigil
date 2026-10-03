package com.sigil.app.ui.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.BlockType
import com.sigil.app.editor.NoteDoc
import com.sigil.app.editor.SEAL_CHAR
import com.sigil.app.editor.SealRef
import com.sigil.app.ui.theme.MonoFont
import com.sigil.app.ui.theme.SigilColors
import com.sigil.app.ui.theme.UiFont

/** Where a chip landed in the displayed text, for drawing its capsule and catching taps on it. */
data class ChipSpan(val start: Int, val end: Int, val iconStart: Int, val ref: SealRef, val open: Boolean)

/** A checklist line: its index in the note and where its first displayed character is. */
data class CheckSpan(val line: Int, val start: Int, val done: Boolean)

/**
 * Turns a [NoteDoc]'s raw text into what the editor shows: line 0 in the title style, 10px
 * between blocks (14px under the title), checklist lines indented past their circle, and each
 * [SEAL_CHAR] expanded into a chip, "🔒 Door code ••••••", or its value while revealed.
 *
 * A line break can't simply stay a "\n": Compose lays out a paragraph that ends in one with an
 * extra empty line. Paragraphs are split by [ParagraphStyle] ranges instead, each newline
 * becoming a zero-width space that ends its line plus a short zero-width spacer paragraph.
 * The cursor never lands inside a chip or spacer; [OffsetMapping] snaps it to the nearest edge.
 */
class NoteVisual(
    private val doc: NoteDoc,
    private val open: Map<String, String>,
    colors: SigilColors,
    density: Density,
) : VisualTransformation {
    val chips = ArrayList<ChipSpan>()
    val checks = ArrayList<CheckSpan>()
    private val shown: AnnotatedString
    /** The displayed text, to check a layout belongs to this transformation before using its offsets. */
    val shownText: String
    private val o2t: IntArray
    private val t2o: IntArray

    init {
        val src = doc.text
        val b = AnnotatedString.Builder()
        val o2t = IntArray(src.length + 1)
        val t2o = ArrayList<Int>(src.length + 16)
        val indent: TextUnit = with(density) { CHECK_GUTTER.dp.toSp() }
        val chipBase = SpanStyle(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.seal)

        fun put(text: String, mapsTo: Int) {
            for (ch in text) {
                t2o.add(mapsTo)
                b.append(ch)
            }
        }

        var o = 0
        var sealIndex = 0
        val last = doc.lineCount - 1
        for (i in 0..last) {
            val meta = doc.lines[i]
            val ps = b.length
            while (o < src.length && src[o] != '\n') {
                o2t[o] = b.length
                if (src[o] == SEAL_CHAR) {
                    val ref = doc.seals[sealIndex++]
                    val value = open[ref.secretId]
                    val cs = b.length
                    // Edges map to the cursor before/after the chip; the middle snaps to whichever is closer.
                    val before = o
                    val after = o + 1
                    put(PAD, before)
                    val iconStart = b.length
                    put(ICON_SLOT, before)
                    put(PAD, before)
                    if (value == null) {
                        val labelStart = b.length
                        put(ref.label.replace(' ', ' ') + " ", after)
                        val dotsStart = b.length
                        put("••••••", after)
                        b.addStyle(SpanStyle(letterSpacing = 1.sp, color = colors.seal.copy(alpha = 0.8f)), dotsStart, b.length)
                        b.addStyle(SpanStyle(color = colors.seal), labelStart, dotsStart)
                    } else {
                        val vs = b.length
                        put(value.replace(' ', ' '), after)
                        b.addStyle(SpanStyle(fontFamily = MonoFont, color = colors.label), vs, b.length)
                    }
                    put(PAD + PAD, after)
                    b.addStyle(chipBase, cs, b.length)
                    chips.add(ChipSpan(cs, b.length, iconStart, ref, value != null))
                } else {
                    put(src[o].toString(), o)
                }
                o++
            }
            val contentEnd = b.length

            when {
                i == 0 -> b.addStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em), ps, contentEnd)
                meta.type == BlockType.HEADING -> b.addStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), ps, contentEnd)
                meta.isCheck && meta.done -> b.addStyle(SpanStyle(color = colors.label3), ps, contentEnd)
            }
            if (i > 0 && meta.isCheck) checks.add(CheckSpan(i, ps, meta.done))

            val paragraph = when {
                i == 0 -> ParagraphStyle(lineHeight = 31.sp, lineHeightStyle = NO_TRIM)
                meta.isCheck -> ParagraphStyle(textIndent = TextIndent(indent, indent), lineHeightStyle = NO_TRIM)
                else -> ParagraphStyle(lineHeightStyle = NO_TRIM)
            }
            if (i < last) {
                o2t[o] = b.length
                put(ZWSP, o)
                b.addStyle(paragraph, ps, b.length)
                val gap = if (i == 0) 14 else 10
                val spacer = b.length
                put(ZWSP, o)
                b.addStyle(ParagraphStyle(lineHeight = with(density) { gap.dp.toSp() }, lineHeightStyle = NO_TRIM), spacer, b.length)
                b.addStyle(SpanStyle(fontSize = 2.sp), spacer, b.length)
                o++
            } else {
                b.addStyle(paragraph, ps, b.length)
            }
        }
        o2t[src.length] = b.length
        t2o.add(src.length)

        this.shown = b.toAnnotatedString()
        this.shownText = shown.text
        this.o2t = o2t
        this.t2o = t2o.toIntArray()
    }

    private val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, o2t.size - 1)]
        override fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, t2o.size - 1)]
    }

    override fun filter(text: AnnotatedString): TransformedText =
        if (text.text == doc.text) TransformedText(shown, mapping) else TransformedText(text, OffsetMapping.Identity)

    /** The capsule(s) behind a chip; two when a chip wraps onto the next line. */
    fun chipRects(layout: TextLayoutResult, chip: ChipSpan, density: Density): List<Rect> = with(density) {
        val n = layout.layoutInput.text.length
        if (chip.end > n) return emptyList()
        val first = layout.getLineForOffset(chip.start)
        val lastLine = layout.getLineForOffset(chip.end - 1)
        val left = layout.getBoundingBox(chip.start).left
        val right = layout.getBoundingBox(chip.end - 1).right
        fun band(line: Int, l: Float, r: Float): Rect {
            val base = layout.getLineBaseline(line)
            return Rect(l, base - 16.5.dp.toPx(), r, base + 5.5.dp.toPx())
        }
        if (first == lastLine) listOf(band(first, left, right))
        else listOf(band(first, left, layout.getLineRight(first)), band(lastLine, layout.getLineLeft(lastLine), right))
    }

    fun iconCenterX(layout: TextLayoutResult, chip: ChipSpan): Float =
        (layout.getBoundingBox(chip.iconStart).left + layout.getBoundingBox(chip.iconStart + 1).right) / 2f

    /** The circle's hit area: the gutter beside the item's first line. */
    fun checkRect(layout: TextLayoutResult, check: CheckSpan, density: Density): Rect = with(density) {
        val line = layout.getLineForOffset(check.start.coerceAtMost(layout.layoutInput.text.length))
        Rect(0f, layout.getLineTop(line), CHECK_GUTTER.dp.toPx(), layout.getLineBottom(line))
    }

    companion object {
        private const val ZWSP = "​"
        /** Narrow no-break space: the capsule's inner padding, without letting a chip wrap. */
        private const val PAD = " "
        /** Two figure spaces reserve room for the padlock, drawn over them. */
        private const val ICON_SLOT = "  "
        const val CHECK_GUTTER = 30
        private val NO_TRIM = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    }
}
