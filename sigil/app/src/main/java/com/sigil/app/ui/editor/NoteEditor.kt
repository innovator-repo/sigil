package com.sigil.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.sigil.app.editor.NoteDoc
import com.sigil.app.editor.SealRef
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType

/** Editor state for one open note: the document model plus the text field's own value (text, cursor, IME composition). */
@Stable
class NoteEditorState(initial: NoteDoc, cursorAtEnd: Boolean) {
    var doc by mutableStateOf(initial)
        private set
    var value by mutableStateOf(TextFieldValue(initial.text, TextRange(if (cursorAtEnd) initial.text.length else 0)))
        private set
    var focused by mutableStateOf(false)

    /** Applies a text field change; returns true if the note's content changed. */
    fun onValueChange(next: TextFieldValue): Boolean {
        val prev = value
        if (next.text == prev.text) {
            value = next
            return false
        }
        val oldCursor = if (prev.selection.collapsed) prev.selection.start else -1
        val newCursor = if (next.selection.collapsed) next.selection.start else -1
        val out = doc.applyEdit(next.text, oldCursor, newCursor)
        val changed = out.doc != doc
        doc = out.doc
        value = if (out.cursor != null) TextFieldValue(out.doc.text, TextRange(out.cursor)) else next
        return changed
    }

    fun update(newDoc: NoteDoc, cursor: Int? = null) {
        doc = newDoc
        value = if (cursor != null || newDoc.text != value.text) {
            TextFieldValue(newDoc.text, TextRange((cursor ?: value.selection.start).coerceIn(0, newDoc.text.length)))
        } else {
            value
        }
    }

    val cursorLine: Int get() = doc.lineIndexAt(value.selection.start)
}

/**
 * The note page: serif "paper", the first line as the title, checklists with round
 * checkboxes, and sealed chips that open only for the person holding the phone.
 */
@Composable
fun NoteEditor(
    state: NoteEditorState,
    openChips: Map<String, String>,
    readOnly: Boolean,
    focusRequester: FocusRequester,
    onEdited: () -> Unit,
    onChipTap: (SealRef) -> Unit,
    onToggleDone: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sigil.colors
    val density = LocalDensity.current
    val visual = remember(state.doc, openChips, c, density) { NoteVisual(state.doc, openChips, c, density) }
    val layoutHolder = remember { mutableStateOf<TextLayoutResult?>(null) }
    val lock = rememberVectorPainter(SigilIcon.Lock.vector(2.2f))
    val currentVisual by rememberUpdatedState(visual)
    val chipTap by rememberUpdatedState(onChipTap)
    val doneTap by rememberUpdatedState(onToggleDone)

    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = state.value,
            onValueChange = { if (state.onValueChange(it)) onEdited() },
            readOnly = readOnly,
            textStyle = SigilType.noteBody.copy(
                color = c.label,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
            cursorBrush = SolidColor(c.tint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            visualTransformation = visual,
            onTextLayout = { layoutHolder.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state.focused = it.isFocused }
                .pointerInput(Unit) {
                    // Taps on a chip or a checkbox are theirs, not the text field's.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val layout = layoutHolder.value ?: return@awaitEachGesture
                        val v = currentVisual
                        if (layout.layoutInput.text.text != v.shownText) return@awaitEachGesture
                        val slop = 6.dp.toPx()
                        val chip = v.chips.firstOrNull { ch ->
                            v.chipRects(layout, ch, this).any { r -> r.inflate(slop).contains(down.position) }
                        }
                        val check = if (chip == null) v.checks.firstOrNull { ck -> v.checkRect(layout, ck, this).contains(down.position) } else null
                        if (chip == null && check == null) return@awaitEachGesture
                        down.consume()
                        val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                        up.consume()
                        if (chip != null) chipTap(chip.ref) else if (check != null && !readOnly) doneTap(check.line)
                    }
                }
                .drawBehind {
                    val layout = layoutHolder.value ?: return@drawBehind
                    // Skip the frame where the text changed but hasn't been laid out yet.
                    if (layout.layoutInput.text.text != visual.shownText) return@drawBehind
                    for (chip in visual.chips) {
                        val rects = visual.chipRects(layout, chip, this)
                        for (r in rects) {
                            drawRoundRect(
                                color = if (chip.open) c.fill else c.sealSoft,
                                topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(7.dp.toPx()),
                            )
                            if (!chip.open) {
                                drawRoundRect(
                                    color = c.seal.copy(alpha = 0.4f), topLeft = r.topLeft, size = r.size,
                                    cornerRadius = CornerRadius(7.dp.toPx()), style = Stroke(0.5.dp.toPx()),
                                )
                            }
                        }
                        val first = rects.firstOrNull() ?: continue
                        val s = 13.dp.toPx()
                        val cx = visual.iconCenterX(layout, chip)
                        translate(cx - s / 2, first.center.y - s / 2) {
                            with(lock) { draw(Size(s, s), colorFilter = ColorFilter.tint(c.seal)) }
                        }
                    }
                    for (check in visual.checks) {
                        val r = visual.checkRect(layout, check, this)
                        val radius = 10.dp.toPx()
                        val center = Offset(radius, r.center.y)
                        if (check.done) {
                            drawCircle(c.note, radius, center)
                            val p = Path().apply {
                                moveTo(center.x - 4.5.dp.toPx(), center.y + 0.2.dp.toPx())
                                lineTo(center.x - 1.4.dp.toPx(), center.y + 3.dp.toPx())
                                lineTo(center.x + 4.5.dp.toPx(), center.y - 3.dp.toPx())
                            }
                            drawPath(p, c.onColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        } else {
                            drawCircle(c.label3, radius - 0.75.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                },
        )
        if (state.doc.text.isEmpty()) {
            Text(
                "Start writing. The first line becomes the title.",
                style = SigilType.noteTitle, color = c.label3,
            )
        }
    }
}
