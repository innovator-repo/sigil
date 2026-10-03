package com.sigil.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.sigil.app.ui.components.tapQuiet
import com.sigil.app.ui.overlays.ConfirmDeleteSheet
import com.sigil.app.ui.overlays.FindBar
import com.sigil.app.ui.overlays.ItemEditorSheet
import com.sigil.app.ui.overlays.NewItemSheet
import com.sigil.app.ui.overlays.GeneratorSheet
import com.sigil.app.ui.overlays.LockScreen
import com.sigil.app.ui.overlays.ToastHost
import com.sigil.app.ui.overlays.TotpSetupSheet
import com.sigil.app.ui.screens.CheckupPane
import com.sigil.app.ui.screens.DetailPane
import com.sigil.app.ui.screens.ListPane
import com.sigil.app.ui.screens.SidebarPane
import com.sigil.app.ui.theme.Sigil

/** The prototype's container breakpoints: one pane on a phone, list + detail, then all three. */
enum class WindowMode { COMPACT, MEDIUM, EXPANDED }

@Composable
fun SigilApp(state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .onPreviewKeyEvent { e ->
                // ⌘K / Ctrl+K opens the Find bar with a hardware keyboard, as on the Mac.
                if (e.type == KeyEventType.KeyDown && e.key == Key.K && (e.isCtrlPressed || e.isMetaPressed) && !state.locked) {
                    vm.openFind()
                    true
                } else {
                    false
                }
            },
    ) {
        val mode = when {
            maxWidth < 700.dp -> WindowMode.COMPACT
            maxWidth < 980.dp -> WindowMode.MEDIUM
            else -> WindowMode.EXPANDED
        }

        BackHandler(enabled = !state.locked && (state.overlay != null || state.sidebarOpen || state.selecting || (mode == WindowMode.COMPACT && state.pane != Pane.SIDEBAR))) {
            when {
                state.overlay != null -> vm.closeOverlay()
                state.selecting -> vm.endSelection()
                state.sidebarOpen -> vm.setSidebarOpen(false)
                state.pane == Pane.DETAIL && state.section == Section.CHECKUP -> vm.showSidebar()
                state.pane == Pane.DETAIL -> vm.showList()
                else -> vm.showSidebar()
            }
        }

        val blur = if (state.locked && Build.VERSION.SDK_INT >= 31) Modifier.blur(34.dp) else Modifier
        Box(Modifier.fillMaxSize().then(blur)) {
            if (state.ready) {
                when (mode) {
                    WindowMode.COMPACT -> CompactPanes(state, vm)
                    WindowMode.MEDIUM -> Row(Modifier.fillMaxSize()) {
                        if (state.section != Section.CHECKUP) {
                            ListPane(state, vm, mode, Modifier.width(320.dp).fillMaxHeight())
                            VerticalHairline()
                        }
                        Detail(state, vm, mode, Modifier.weight(1f).fillMaxHeight())
                    }
                    WindowMode.EXPANDED -> Row(Modifier.fillMaxSize()) {
                        SidebarPane(state, vm, mode, Modifier.width(232.dp).fillMaxHeight().background(c.bg))
                        VerticalHairline()
                        if (state.section != Section.CHECKUP) {
                            ListPane(state, vm, mode, Modifier.width(320.dp).fillMaxHeight())
                            VerticalHairline()
                        }
                        Detail(state, vm, mode, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }

            if (mode == WindowMode.MEDIUM) {
                AnimatedVisibility(state.sidebarOpen, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().background(c.scrim).tapQuiet { vm.setSidebarOpen(false) })
                }
                AnimatedVisibility(
                    state.sidebarOpen,
                    enter = slideInHorizontally { -it },
                    exit = slideOutHorizontally { -it },
                ) {
                    SidebarPane(state, vm, mode, Modifier.width(260.dp).fillMaxHeight().shadow(24.dp).background(c.paper))
                }
            }
        }

        when (val o = state.overlay) {
            is Overlay.Find -> FindBar(o.initial, state, vm)
            is Overlay.Generator -> GeneratorSheet(o.changeItemId?.let { state.item(it) }, state, vm)
            is Overlay.TotpSetup -> state.item(o.itemId)?.let { TotpSetupSheet(it, vm) }
            is Overlay.ConfirmDelete -> ConfirmDeleteSheet(o.ids, state, vm)
            Overlay.NewItem -> NewItemSheet(vm)
            is Overlay.Editor -> ItemEditorSheet(o, vm)
            null -> {}
        }

        if (state.locked) LockScreen(vm)

        ToastHost(state.toast, vm)
    }
}

@Composable
private fun Detail(state: UiState, vm: SigilViewModel, mode: WindowMode, modifier: Modifier) {
    if (state.section == Section.CHECKUP) CheckupPane(state, vm, mode, modifier) else DetailPane(state, vm, mode, modifier)
}

@Composable
private fun CompactPanes(state: UiState, vm: SigilViewModel) {
    AnimatedContent(
        targetState = state.pane,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(320)) { w -> dir * w } togetherWith slideOutHorizontally(tween(320)) { w -> -dir * w / 3 })
        },
        label = "pane",
    ) { pane ->
        when (pane) {
            Pane.SIDEBAR -> SidebarPane(state, vm, WindowMode.COMPACT, Modifier.fillMaxSize().background(Sigil.colors.paper))
            Pane.LIST -> ListPane(state, vm, WindowMode.COMPACT, Modifier.fillMaxSize())
            Pane.DETAIL -> Detail(state, vm, WindowMode.COMPACT, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VerticalHairline() {
    Box(Modifier.width(0.5.dp).fillMaxHeight().background(Sigil.colors.sep))
}
