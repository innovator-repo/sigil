package com.sigil.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.subtitleText
import com.sigil.app.data.model.titleText
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.WindowMode
import com.sigil.app.ui.components.BackButton
import com.sigil.app.ui.components.GroupLabel
import com.sigil.app.ui.components.ItemGlyph
import com.sigil.app.ui.components.ToolButton
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.TimeFormat

private sealed interface Entry {
    val key: String
    data class Group(val label: String) : Entry { override val key get() = "g:$label" }
    data class Row(val item: Item, val first: Boolean) : Entry { override val key get() = item.id }
}

/** Items in the current section, pinned first, then grouped by when they were last touched. */
private fun entries(state: UiState): List<Entry> {
    val list = state.listItems()
    val pinGroup = state.section != Section.PINNED && state.section != Section.DELETED
    val ordered = if (pinGroup) list.filter { it.pinned } + list.filterNot { it.pinned } else list
    val now = System.currentTimeMillis()
    val out = ArrayList<Entry>()
    var last: String? = null
    for (i in ordered) {
        val g = if (pinGroup && i.pinned) "Pinned" else TimeFormat.bucket(i.updated, now).label
        val first = g != last
        if (first) out.add(Entry.Group(g))
        out.add(Entry.Row(i, first))
        last = g
    }
    return out
}

@Composable
fun ListPane(state: UiState, vm: SigilViewModel, mode: WindowMode, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val section = state.section
    val entries = entries(state)
    val flagged = state.flaggedIds
    val listState = rememberLazyListState()
    LaunchedEffect(section) { listState.scrollToItem(0) }

    Column(modifier.background(c.paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = if (mode == WindowMode.COMPACT) 14.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                WindowMode.COMPACT -> BackButton("Library") { vm.showSidebar() }
                WindowMode.MEDIUM -> ToolButton(SigilIcon.Sidebar, "Show sidebar", { vm.setSidebarOpen(true) })
                WindowMode.EXPANDED -> Text(
                    section.label, style = SigilType.listTitleWide, color = c.label,
                    modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            ToolButton(SigilIcon.Search, "Find or keep", { vm.openFind() })
            if (section != Section.DELETED) {
                ToolButton(SigilIcon.Key, "New password", { vm.openGenerator() })
                ToolButton(SigilIcon.Compose, "New note", { vm.newNote() })
            }
        }
        if (mode != WindowMode.EXPANDED) {
            Text(
                section.label, style = SigilType.largeTitle, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            )
        }

        if (entries.isEmpty()) {
            Text(
                if (section == Section.DELETED) "Nothing here. Deleted items stay for 30 days." else "Nothing here yet.",
                style = SigilType.small, color = c.label3, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
            )
            return@Column
        }

        val nav = WindowInsets.navigationBars.asPaddingValues()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp + nav.calculateBottomPadding()),
        ) {
            items(entries, key = { it.key }, contentType = { it::class }) { e ->
                when (e) {
                    is Entry.Group -> GroupLabel(e.label)
                    is Entry.Row -> {
                        val index = entries.indexOf(e)
                        val prevSelected = (entries.getOrNull(index - 1) as? Entry.Row)?.item?.id == state.selectedId
                        ItemRow(
                            item = e.item,
                            selected = e.item.id == state.selectedId,
                            divider = !e.first && !prevSelected,
                            flagged = e.item.id in flagged,
                            onClick = { vm.select(e.item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, selected: Boolean, divider: Boolean, flagged: Boolean, onClick: () -> Unit) {
    val c = Sigil.colors
    val sep = c.sep
    val onSel = c.onColor
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider && !selected) drawLine(sep, Offset(48.dp.toPx(), 0f), Offset(size.width - 8.dp.toPx(), 0f), 0.5.dp.toPx())
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.sel else Color.Transparent)
            .tap(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected && item is com.sigil.app.data.model.NoteItem) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                com.sigil.app.ui.components.SIcon(SigilIcon.Note, onSel, 17.dp)
            }
        } else {
            ItemGlyph(item, 30.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.titleText(), style = SigilType.rowTitle, color = if (selected) onSel else c.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (selected) onSel.copy(alpha = 0.82f) else c.label)) {
                        append(TimeFormat.shortTime(item.updated))
                    }
                    append("  ")
                    append(item.subtitleText())
                },
                style = SigilType.rowSub, color = if (selected) onSel.copy(alpha = 0.82f) else c.label2,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (flagged) Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) onSel else c.caution))
    }
}
