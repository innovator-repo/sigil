package com.sigil.app.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.isPassword
import com.sigil.app.data.model.searchableBody
import com.sigil.app.data.model.subtitleText
import com.sigil.app.data.model.titleText
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.components.Hairline
import com.sigil.app.ui.components.ItemGlyph
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.components.tapQuiet
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.Search
import com.sigil.core.SearchDoc

private class Option(
    val group: String,
    val glyph: @Composable () -> Unit,
    val title: AnnotatedString,
    val sub: AnnotatedString?,
    val key: String = "",
    val run: () -> Unit,
)

private data class Command(val title: String, val words: String, val icon: SigilIcon, val run: (SigilViewModel) -> Unit)

private val COMMANDS = listOf(
    Command("New Note", "new note write capture", SigilIcon.Compose) { it.newNote() },
    Command("New Password", "new password login sign in generate", SigilIcon.Key) { it.openGenerator() },
    Command("New Account or Card", "new account bank card wifi lock license item save", SigilIcon.Plus2) { it.openNewItem() },
    Command("Check-up", "check up security health", SigilIcon.Shield) { it.go(Section.CHECKUP) },
    Command("Lock Sigil", "lock", SigilIcon.Lock) { it.lock() },
)

/** Bold-tint the word starts that match the query, like the prototype's <mark>. */
private fun highlight(text: String, q: List<String>, tint: Color): AnnotatedString {
    if (q.isEmpty()) return AnnotatedString(text)
    val re = Regex("(^|[^\\p{L}\\p{N}])(${q.joinToString("|") { Regex.escape(it) }})", RegexOption.IGNORE_CASE)
    return buildAnnotatedString {
        var last = 0
        for (m in re.findAll(text)) {
            val g = m.groups[2] ?: continue
            append(text.substring(last, g.range.first))
            withStyle(SpanStyle(color = tint, fontWeight = FontWeight.SemiBold)) { append(g.value) }
            last = g.range.last + 1
        }
        append(text.substring(last))
    }
}

/** "Find or keep": one field that searches everything, and keeps what you type as a new note if nothing matches. */
@Composable
fun FindBar(initial: String, state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    var query by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val raw = query.text.trim()
    val q = Search.queryTokens(raw)
    val live = state.items.filter { !it.isDeleted }
    val itemOpt = { group: String, i: Item ->
        val sub = if (i is NoteItem) i.preview else i.subtitleText() + ((i as? com.sigil.app.data.model.LoginItem)?.url?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        Option(group, { ItemGlyph(i, 28.dp) }, highlight(i.titleText(), q, c.tint), highlight(sub, q, c.tint)) { vm.open(i.id) }
    }
    val cmdOpt = { cmd: Command ->
        Option("Actions", {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(c.fill), contentAlignment = Alignment.Center) { SIcon(cmd.icon, c.label2, 16.dp) }
        }, AnnotatedString(cmd.title), null) { cmd.run(vm) }
    }

    val options = ArrayList<Option>()
    if (q.isEmpty()) {
        live.sortedByDescending { it.updated }.take(4).forEach { options.add(itemOpt("Recent", it)) }
        COMMANDS.forEach { options.add(cmdOpt(it)) }
    } else {
        val docs = live.map { SearchDoc(it.id, it.titleText(), "${it.searchableBody()} ${it.space.label}", it.pinned, it.updated) }
        val hits = docs.mapNotNull { d -> Search.score(d, q)?.let { d to it } }
            .sortedWith(compareByDescending<Pair<SearchDoc, Double>> { it.second }.thenByDescending { it.first.updated })
            .mapNotNull { (d, _) -> live.firstOrNull { it.id == d.id } }
        val rest = hits.drop(1).take(8)
        val shown = if (raw.length > 48) raw.take(47) + "…" else raw
        val keep = Option(
            "Keep",
            { Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(c.note.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { SIcon(SigilIcon.Compose, c.note, 16.dp) } },
            AnnotatedString("New note “$shown”"),
            AnnotatedString("Press Return to keep it"),
            key = if (hits.isEmpty()) "↩" else "",
        ) { vm.newNote(raw) }
        if (hits.isEmpty()) options.add(keep)
        hits.take(1).forEach { options.add(itemOpt("Top Hit", it)) }
        rest.filter { it is NoteItem }.forEach { options.add(itemOpt("Notes", it)) }
        rest.filter { it.isPassword() }.forEach { options.add(itemOpt("Passwords", it)) }
        COMMANDS.filter { cmd -> q.all { x -> cmd.words.split(' ').any { it.startsWith(x) } } }.forEach { options.add(cmdOpt(it)) }
        if (hits.isNotEmpty()) options.add(keep)
    }

    fun run(o: Option) {
        vm.closeOverlay()
        o.run()
    }

    Box(Modifier.fillMaxSize().background(c.scrim).tapQuiet { vm.closeOverlay() }.statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .shadow(28.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.glassStrong).tapQuiet {},
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(SigilIcon.Search, c.label2, 20.dp)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (query.text.isEmpty()) Text("Find or keep anything", style = SigilType.searchInput, color = c.label3, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = SigilType.searchInput.copy(color = c.label),
                        cursorBrush = SolidColor(c.tint),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { options.firstOrNull()?.let(::run) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Text(
                    "Cancel", style = SigilType.small, color = c.tint,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).tap { vm.closeOverlay() }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (options.isNotEmpty()) {
                Hairline()
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(6.dp)) {
                    itemsIndexed(options) { i, o ->
                        if (i == 0 || options[i - 1].group != o.group) {
                            Text(o.group, style = SigilType.group, color = c.label3, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp))
                        }
                        val active = i == 0
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(if (active) c.sel else Color.Transparent)
                                .tap { run(o) }.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            o.glyph()
                            Column(Modifier.weight(1f)) {
                                Text(o.title, style = SigilType.rowTitle, fontWeight = FontWeight.Medium, color = if (active) c.onColor else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                o.sub?.let { Text(it, style = SigilType.rowSub, color = if (active) c.onColor.copy(alpha = 0.86f) else c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (o.key.isNotEmpty()) Text(o.key, style = SigilType.meta, color = if (active) c.onColor else c.label3)
                        }
                    }
                }
            }
            Hairline()
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("↩ to open", style = SigilType.meta, color = c.label3)
                Text("Sealed values are never searched.", style = SigilType.meta, color = c.label3)
            }
        }
    }
}
