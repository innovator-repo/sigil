package com.sigil.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.subtitleText
import com.sigil.app.designsystem.CircadianPhase
import com.sigil.app.designsystem.LumeMotion
import com.sigil.app.logic.NowModel
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.components.AppIcon
import com.sigil.app.ui.components.GroupLabel
import com.sigil.app.ui.components.ItemGlyph
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.onAccent
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.components.tapQuiet
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PEEK = 170.dp

/**
 * Sigil's one main screen: a small header, the hero (what is on your mind, a New note button and
 * a way back into the last note), and a pull-up drawer holding the notes, search and the Vault
 * row, with three shortcut chips on its lip. There is no tab bar; everything else is one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowScreen(state: UiState, vm: SigilViewModel, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val anim = remember { arrayOfNulls<Job>(1) }
    val open by remember { derivedStateOf { progress > 0.02f } }

    val notes = remember(state.items) { NowModel.drawerNotes(state.items) }
    val last = remember(state.items) { NowModel.lastNote(state.items) }
    val summary = remember(state.items) { NowModel.summary(state.items) }
    val vaultCount = remember(state.items) { NowModel.vaultCount(state.items) }
    val deletedCount = remember(state.items) { NowModel.deletedCount(state.items) }
    val phase = remember { CircadianPhase.now() }

    suspend fun settle(target: Float) {
        animate(progress, target, animationSpec = LumeMotion.spatial()) { v, _ -> progress = v.coerceIn(0f, 1f) }
        progress = target
    }
    fun settleTo(target: Float) {
        anim[0]?.cancel()
        anim[0] = scope.launch { settle(target) }
    }
    fun targetFor(velocity: Float): Float = when {
        velocity < -700f -> 1f
        velocity > 700f -> 0f
        progress > 0.5f -> 1f
        else -> 0f
    }

    BackHandler(enabled = open) { settleTo(0f) }

    BoxWithConstraints(modifier.fillMaxSize().background(c.bg)) {
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val peekPx = with(density) { PEEK.toPx() } + navBottom
        val openPx = maxOf(constraints.maxHeight * 0.82f, peekPx + 1f)
        val travel = openPx - peekPx
        val peekDp = with(density) { peekPx.toDp() }

        val nested = remember(travel) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0f && progress < 1f) {
                        anim[0]?.cancel()
                        val old = progress
                        progress = (progress - available.y / travel).coerceIn(0f, 1f)
                        return Offset(0f, -(progress - old) * travel)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0f && progress > 0f) {
                        val old = progress
                        progress = (progress - available.y / travel).coerceIn(0f, 1f)
                        return Offset(0f, (old - progress) * travel)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (progress > 0f && progress < 1f) {
                        settle(targetFor(available.y))
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // ── Header and hero ──
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "SIGIL · ${phaseWord(phase)}", style = SigilType.kicker, color = c.label2,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(48.dp).clip(CircleShape).tap { vm.showSettings() }
                        .semantics { contentDescription = "Settings" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
                        SIcon(SigilIcon.Gear, c.label2, 18.dp)
                    }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = peekDp)
                    .graphicsLayer { alpha = 1f - 0.9f * progress },
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("WRITE", style = SigilType.kicker, color = c.label3)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "What is on your mind?",
                        style = SigilType.largeTitle.copy(fontSize = 34.sp, lineHeight = 40.sp),
                        color = c.label, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Opens a new note, saved on this phone", style = SigilType.body, color = c.label2, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(c.tint)
                            .tap { vm.newNote() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("New note", style = SigilType.sheetTitle, color = c.tint.onAccent())
                    }
                    if (last != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).tap { vm.select(last.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Continue: ${last.title}", style = SigilType.body, color = c.label2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Pull-up drawer ──
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { openPx.toDp() })
                .offset { IntOffset(0, ((1f - progress) * travel).roundToInt()) }
                .shadow(8.dp, shape)
                .clip(shape)
                .background(c.paper),
        ) {
            Column(
                Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy ->
                        anim[0]?.cancel()
                        progress = (progress - dy / travel).coerceIn(0f, 1f)
                    },
                    onDragStopped = { v -> settleTo(targetFor(v)) },
                ),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(28.dp)
                        .tapQuiet { settleTo(if (progress > 0.5f) 0f else 1f) }
                        .semantics { contentDescription = if (open) "Close notes drawer" else "Open notes drawer" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(c.label3.copy(alpha = 0.5f)))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", style = SigilType.sheetTitle, color = c.label, modifier = Modifier.weight(1f))
                    Text(summary, style = SigilType.rowSub.copy(fontSize = 14.sp), color = c.label2, maxLines = 1)
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("Vault", SigilIcon.Lock) { vm.go(Section.PASSWORDS) }
                    Chip("Checkup", SigilIcon.Shield, badge = state.checkup.open.size) { vm.go(Section.CHECKUP) }
                    Chip("Clipboard", SigilIcon.Copy) { vm.showClipboard() }
                }
            }

            LazyColumn(
                Modifier.weight(1f).nestedScroll(nested),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp + with(density) { navBottom.toDp() }),
            ) {
                item(key = "search") {
                    DrawerRow(SigilIcon.Search, "Search notes and vault", null) { vm.openFind() }
                }
                item(key = "vault") {
                    DrawerRow(SigilIcon.Lock, "Vault", if (vaultCount == 1) "1 item" else "$vaultCount items") { vm.go(Section.PASSWORDS) }
                }
                val pinned = notes.filter { it.pinned }
                val recent = notes.filterNot { it.pinned }
                if (notes.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No notes yet. Your first one is a tap away.", style = SigilType.body, color = c.label3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    item(key = "g:pinned") { GroupLabel("Pinned") }
                    items(pinned, key = { it.id }) { NoteRow(it) { vm.select(it.id) } }
                }
                if (recent.isNotEmpty()) {
                    item(key = "g:recent") { GroupLabel("Recent") }
                    items(recent, key = { it.id }) { NoteRow(it) { vm.select(it.id) } }
                }
                if (deletedCount > 0) {
                    item(key = "deleted") {
                        Spacer(Modifier.height(8.dp))
                        DrawerRow(SigilIcon.Trash, "Recently deleted", "$deletedCount") { vm.go(Section.DELETED) }
                    }
                }
            }
        }
    }
}

private fun phaseWord(p: CircadianPhase) = when (p) {
    CircadianPhase.MORNING -> "MORNING"
    CircadianPhase.DAY -> "DAY"
    CircadianPhase.EVENING -> "EVENING"
    CircadianPhase.SLEEP -> "NIGHT"
}

/** A rounded shortcut capsule with a 48 dp tap target; [badge] shows a count when above zero. */
@Composable
internal fun Chip(label: String, icon: SigilIcon?, selected: Boolean = false, badge: Int = 0, onClick: () -> Unit) {
    val c = Sigil.colors
    Box(Modifier.heightIn(min = 48.dp).tap(onClick = onClick).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) c.tint else c.fill)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fg = if (selected) c.tint.onAccent() else c.label
            if (icon != null) SIcon(icon, if (selected) fg else c.tint, 16.dp, stroke = 2f)
            Text(label, style = SigilType.button.copy(fontSize = 14.sp), color = fg, maxLines = 1)
            if (badge > 0) {
                Text("$badge", style = SigilType.meta.copy(fontWeight = FontWeight.SemiBold), color = if (selected) fg else c.caution)
            }
        }
    }
}

@Composable
private fun DrawerRow(icon: SigilIcon, title: String, trailing: String?, onClick: () -> Unit) {
    val c = Sigil.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(c.fill2).tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SIcon(icon, c.tint, 20.dp)
        Text(title, style = SigilType.rowTitle.copy(fontSize = 15.sp), color = c.label, modifier = Modifier.weight(1f), maxLines = 1)
        if (trailing != null) Text(trailing, style = SigilType.rowSub.copy(fontSize = 14.sp), color = c.label2, maxLines = 1)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NoteRow(note: NoteItem, onClick: () -> Unit) {
    val c = Sigil.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).tap(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ItemGlyph(note, 30.dp)
        Column(Modifier.weight(1f)) {
            Text(note.title, style = SigilType.rowTitle.copy(fontSize = 15.sp), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${TimeFormat.shortTime(note.updated)}  ${note.subtitleText()}",
                style = SigilType.rowSub.copy(fontSize = 14.sp), color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.pinned) SIcon(SigilIcon.Pin, c.label3, 15.dp)
    }
}
