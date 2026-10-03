package com.sigil.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.editor.NoteDoc
import com.sigil.app.editor.SealRef
import com.sigil.app.ui.LocalPresence
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.WindowMode
import com.sigil.app.ui.components.BackButton
import com.sigil.app.ui.components.Pill
import com.sigil.app.ui.components.SButton
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.ToolButton
import com.sigil.app.ui.editor.NoteEditor
import com.sigil.app.ui.editor.NoteEditorState
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.SecretDetector
import com.sigil.core.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailPane(state: UiState, vm: SigilViewModel, mode: WindowMode, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val item = state.selected?.takeIf(state.section::matches)
    val back: @Composable () -> Unit = {
        if (mode == WindowMode.COMPACT) BackButton(state.section.label) { vm.showList() }
    }
    Box(modifier.background(c.paper).statusBarsPadding()) {
        if (item == null) {
            Column {
                Toolbar { back() }
                Text(
                    "Select something on the left, or start a new note.",
                    style = SigilType.small, color = c.label3, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
                )
            }
        } else if (item is NoteItem) {
            NotePage(item, state, vm, mode, back)
        } else {
            Column {
                Toolbar {
                    back()
                    Spacer(Modifier.weight(1f))
                    ItemActions(item, vm)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Page(mode) {
                        if (item.isDeleted) DeletedBanner(vm)
                        when (item) {
                            is LoginItem -> LoginBody(item, state, vm)
                            is CardItem -> CardBody(item, state, vm)
                            is WifiItem -> WifiBody(item, state, vm)
                            is NoteItem -> {}
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun Toolbar(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 14.dp, bottom = 4.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ItemActions(item: Item, vm: SigilViewModel) {
    if (item.isDeleted) return
    ToolButton(if (item.pinned) SigilIcon.Unpin else SigilIcon.Pin, if (item.pinned) "Unpin" else "Pin", { vm.togglePin() }, tint = if (item.pinned) Sigil.colors.tint else Sigil.colors.label2)
    ToolButton(SigilIcon.Trash, "Delete", { vm.delete() })
}

/** The reading column (`.d-inner`): centered, at most 640dp wide. */
@Composable
fun Page(mode: WindowMode, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .padding(start = if (mode == WindowMode.COMPACT) 18.dp else 28.dp, end = if (mode == WindowMode.COMPACT) 18.dp else 28.dp, top = 4.dp, bottom = 48.dp),
        ) { content() }
    }
}

@Composable
fun DeletedBanner(vm: SigilViewModel) {
    val c = Sigil.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(10.dp)).background(c.fill2).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("In Recently Deleted. Kept for 30 days, then removed.", style = SigilType.small, color = c.label2, modifier = Modifier.weight(1f))
        Spacer(Modifier.padding(start = 12.dp))
        Pill("Recover", { vm.recover() })
    }
}

fun editedLine(item: Item): String = "Edited ${TimeFormat.relativeTime(item.updated)} · ${item.space.label}"

private val SEAL_PHRASE = mapOf(
    "Password" to "a password", "Key" to "a key", "Door code" to "a door code", "PIN" to "a PIN",
    "Wi‑Fi password" to "a Wi‑Fi password", "Card number" to "a card number", "API key" to "an API key",
    "AWS key" to "an AWS key", "GitHub token" to "a GitHub token", "Slack token" to "a Slack token",
)

private data class Suggestion(val line: Int, val value: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotePage(note: NoteItem, state: UiState, vm: SigilViewModel, mode: WindowMode, back: @Composable () -> Unit) {
    val c = Sigil.colors
    val presence = LocalPresence.current
    val scope = rememberCoroutineScope()
    val focusNew = state.focusNoteId == note.id
    val editor = remember(note.id) { NoteEditorState(NoteDoc.fromBlocks(note.blocks), cursorAtEnd = focusNew) }
    val focus = remember(note.id) { FocusRequester() }
    var suggestion by remember(note.id) { mutableStateOf<Suggestion?>(null) }
    val readOnly = note.isDeleted

    LaunchedEffect(note.id, focusNew) {
        if (focusNew) {
            focus.requestFocus()
            vm.consumeFocusRequest()
        }
    }

    // "This looks like a password. Seal it?" - checked a beat after typing stops, on the cursor's line.
    LaunchedEffect(note.id, editor.value.text, editor.value.selection, editor.focused) {
        if (!editor.focused || readOnly) {
            suggestion = null
            return@LaunchedEffect
        }
        delay(450)
        val line = editor.cursorLine
        val found = SecretDetector.findSecrets(editor.doc.lineTextForDetection(line))
            .filter { it.confidence >= 0.6 && !vm.isDismissed(it.value) }
            .lastOrNull()
        suggestion = found?.let { Suggestion(line, it.value, it.label) }
    }

    fun sealRange(start: Int, end: Int, value: String, label: String) {
        scope.launch {
            val id = vm.sealSecret(value)
            // The note may have moved on while encrypting; find the value again before replacing it.
            val doc = editor.doc
            val at = if (doc.text.regionMatches(start, value, 0, value.length) && end - start == value.length) start else doc.text.lastIndexOf(value)
            if (at < 0) return@launch
            val (sealed, cursor) = doc.seal(at, at + value.length, SealRef(id, label))
            editor.update(sealed, cursor)
            vm.onNoteEdited(note.id, sealed)
            vm.noteSealed()
        }
    }

    fun sealSuggestion(s: Suggestion) {
        val doc = editor.doc
        if (s.line >= doc.lineCount) return
        val lineStart = doc.lineStart(s.line)
        val at = doc.lineTextForDetection(s.line).lastIndexOf(s.value)
        if (at < 0) return
        suggestion = null
        sealRange(lineStart + at, lineStart + at + s.value.length, s.value, s.label)
    }

    fun sealSelection() {
        val sel = editor.value.selection
        if (sel.collapsed || !editor.focused) {
            vm.toast("Select text to seal it, or just type a password. Sigil will offer.", SigilIcon.Lock)
            return
        }
        var start = sel.min
        var end = sel.max
        val text = editor.doc.text
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        val chosen = text.substring(start, end)
        if (chosen.isEmpty()) return
        if ('\n' in chosen) {
            vm.toast("Select text within one line to seal it.", SigilIcon.Lock)
            return
        }
        if (com.sigil.app.editor.SEAL_CHAR in chosen) return
        val label = SecretDetector.findSecrets(chosen).firstOrNull()?.label ?: "Secret"
        sealRange(start, end, chosen, label)
    }

    fun toggleChecklist() {
        val line = editor.cursorLine
        if (!editor.focused || line == 0) {
            vm.toast("Put the cursor on a line to turn it into a checklist item.", SigilIcon.Checklist)
            return
        }
        val doc = editor.doc.toggleChecklist(line)
        editor.update(doc)
        vm.onNoteEdited(note.id, doc)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Toolbar {
            back()
            Spacer(Modifier.weight(1f))
            if (!readOnly) {
                ToolButton(SigilIcon.Checklist, "Checklist", { toggleChecklist() })
                ToolButton(SigilIcon.Lock, "Seal selected text", { sealSelection() })
            }
            ItemActions(note, vm)
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Page(mode) {
                    if (readOnly) DeletedBanner(vm)
                    Text(
                        editedLine(note), style = SigilType.meta, color = c.label3, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    )
                    NoteEditor(
                        state = editor,
                        openChips = state.openChips.mapValues { it.value.value },
                        readOnly = readOnly,
                        focusRequester = focus,
                        onEdited = { vm.onNoteEdited(note.id, editor.doc) },
                        onChipTap = { ref ->
                            val opened = state.openChips[ref.secretId]
                            if (opened != null) {
                                vm.copyValue(opened.value, ref.label)
                            } else {
                                presence.require("Reveal ${ref.label.lowercase()}") { vm.openChip(ref.secretId) }
                            }
                        },
                        onToggleDone = { line ->
                            val doc = editor.doc.toggleDone(line)
                            editor.update(doc)
                            vm.onNoteEdited(note.id, doc)
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(72.dp))
            }

            val s = suggestion
            androidx.compose.animation.AnimatedVisibility(
                visible = s != null,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            ) {
                val shown = s ?: return@AnimatedVisibility
                FlowRow(
                    Modifier.shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(c.glassStrong)
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        SIcon(SigilIcon.Lock, c.seal, 16.dp)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("This looks like ${SEAL_PHRASE[shown.label] ?: "a secret"}. Seal it?", style = SigilType.small, color = c.label)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SButton("Not a Secret", {
                            vm.dismissSecret(shown.value)
                            suggestion = null
                        })
                        SButton("Seal", { sealSuggestion(shown) }, primary = true)
                    }
                }
            }
        }
    }
}
