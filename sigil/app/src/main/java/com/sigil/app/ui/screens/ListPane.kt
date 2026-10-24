package com.sigil.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sigil.app.data.model.Space
import com.sigil.app.ui.components.Hairline
import com.sigil.app.ui.components.SIcon
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

    val selection = state.selection
    Column(modifier.background(c.paper).statusBarsPadding()) {
        if (selection != null) {
            SelectionHeader(state, vm, mode)
        } else Row(
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
            if (entries.isNotEmpty()) ToolButton(SigilIcon.Checklist, "Select", { vm.beginSelection() })
            ToolButton(SigilIcon.Search, "Find or keep", { vm.openFind() })
            if (section != Section.DELETED) {
                ToolButton(SigilIcon.Key, "New password or account", { vm.openNewItem() })
                ToolButton(SigilIcon.Compose, "New note", { vm.newNote() })
            }
        }
        if (mode != WindowMode.EXPANDED || selection != null) {
            Text(
                if (selection != null) selectedTitle(selection.size) else section.label, style = SigilType.largeTitle, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp + nav.calculateBottomPadding()),
        ) {
            items(entries, key = { it.key }, contentType = { it::class }) { e ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                when (e) {
                    is Entry.Group -> GroupLabel(e.label)
                    is Entry.Row -> {
                        val index = entries.indexOf(e)
                        val prevSelected = (entries.getOrNull(index - 1) as? Entry.Row)?.item?.id == state.selectedId
                        ItemRow(
                            item = e.item,
                            selected = selection == null && e.item.id == state.selectedId,
                            divider = !e.first && (selection != null || !prevSelected),
                            flagged = e.item.id in flagged,
                            picked = selection?.let { e.item.id in it },
                            onClick = { if (selection != null) vm.toggleSelection(e.item.id) else vm.select(e.item.id) },
                            onLongClick = { if (selection == null) vm.startSelection(e.item.id) else vm.toggleSelection(e.item.id) },
                        )
                    }
                }
            }
}
        }
        if (selection != null) SelectionBar(state, vm)
    }
}

private fun selectedTitle(n: Int) = when (n) {
    0 -> "Select Items"
    1 -> "1 Selected"
    else -> "$n Selected"
}

@Composable
private fun SelectionHeader(state: UiState, vm: SigilViewModel, mode: WindowMode) {
    val c = Sigil.colors
    val all = state.listItems().size
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (mode == WindowMode.COMPACT) 14.dp else 8.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton("Cancel") { vm.endSelection() }
        Spacer(Modifier.weight(1f))
        TextButton(if (state.selection?.size == all && all > 0) "Deselect All" else "Select All") { vm.selectAll() }
    }
}

@Composable
private fun TextButton(label: String, color: Color = Sigil.colors.tint, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, style = SigilType.back, color = if (enabled) color else color.copy(alpha = 0.35f),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).tap(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** What can be done to everything picked: pin, move to a Space, delete - or recover / delete for good in Recently Deleted. */
@Composable
private fun SelectionBar(state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    val picked = state.listItems().filter { it.id in (state.selection ?: emptySet()) }
    val any = picked.isNotEmpty()
    Column(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding()) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (state.section == Section.DELETED) {
                BarAction(SigilIcon.Refresh, "Recover", c.tint, any) { vm.recoverSelected() }
                BarAction(SigilIcon.Trash, "Delete", c.critical, any) { vm.confirmDeleteSelected() }
            } else {
                val allPinned = any && picked.all { it.pinned }
                BarAction(if (allPinned) SigilIcon.Unpin else SigilIcon.Pin, if (allPinned) "Unpin" else "Pin", c.tint, any) { vm.pinSelected() }
                Box {
                    var open by remember { mutableStateOf(false) }
                    BarAction(SigilIcon.People, "Move", c.tint, any) { open = true }
                    DropdownMenu(open, { open = false }, containerColor = c.glassStrong) {
                        for (space in Space.entries) {
                            DropdownMenuItem(
                                text = { Text(space.label, style = SigilType.body, color = c.label) },
                                onClick = {
                                    open = false
                                    vm.moveSelected(space)
                                },
                            )
                        }
                    }
                }
                BarAction(SigilIcon.Trash, "Delete", c.critical, any) { vm.deleteSelected() }
            }
        }
    }
}

@Composable
private fun BarAction(icon: SigilIcon, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) color else color.copy(alpha = 0.35f)
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).tap(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SIcon(icon, tint, 21.dp)
        Text(label, style = SigilType.meta, color = tint, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: Item,
    selected: Boolean,
    divider: Boolean,
    flagged: Boolean,
    picked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
            .background(if (picked == true) c.tintSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected || picked == true }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (picked != null) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (picked) c.tint else Color.Transparent)
                    .border(1.5.dp, if (picked) c.tint else c.label3, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (picked) SIcon(SigilIcon.Check, c.onColor, 14.dp, stroke = 2.6f) }
        }
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
