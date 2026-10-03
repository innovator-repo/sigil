package com.sigil.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.WindowMode
import com.sigil.app.ui.components.Hairline
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType

/** The Library: find, the four library views, the three Spaces, Check-up, Recently Deleted, and Lock. */
@Composable
fun SidebarPane(state: UiState, vm: SigilViewModel, mode: WindowMode, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val compact = mode == WindowMode.COMPACT
    Column(modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = if (compact) 22.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            SidebarRow(SigilIcon.Search, "Find or keep", c.tint, current = false, onClick = { vm.openFind() })

            Head("Library")
            for (s in listOf(Section.ALL, Section.NOTES, Section.PASSWORDS, Section.PINNED)) {
                SidebarRow(s.icon, s.label, c.tint, state.section == s, count = state.count(s)) { vm.go(s) }
            }

            Head("Spaces")
            SidebarRow(Section.PERSONAL.icon, "Personal", c.tint, state.section == Section.PERSONAL, count = state.count(Section.PERSONAL)) { vm.go(Section.PERSONAL) }
            SidebarRow(Section.FAMILY.icon, "Family", c.tiles[2], state.section == Section.FAMILY, count = state.count(Section.FAMILY)) { vm.go(Section.FAMILY) }
            SidebarRow(Section.WORK.icon, "Work", c.tiles[1], state.section == Section.WORK, count = state.count(Section.WORK)) { vm.go(Section.WORK) }

            Spacer(Modifier.height(14.dp))
            val open = state.checkup.open.size
            SidebarRow(
                SigilIcon.Shield, "Check-up", c.seal, state.section == Section.CHECKUP,
                trailing = {
                    if (open > 0) {
                        Box(
                            Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(c.seal)
                                .padding(horizontal = 6.dp).semantics { contentDescription = "$open to look at" },
                            contentAlignment = Alignment.Center,
                        ) { Text("$open", style = SigilType.count, color = c.onColor, fontWeight = FontWeight.SemiBold) }
                    } else {
                        SIcon(SigilIcon.Check, c.label3, 15.dp)
                    }
                },
            ) { vm.go(Section.CHECKUP) }
            SidebarRow(SigilIcon.Trash, "Recently Deleted", c.tint, state.section == Section.DELETED, count = state.count(Section.DELETED)) { vm.go(Section.DELETED) }
        }
        Hairline(Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        SidebarRow(SigilIcon.Lock, "Lock Sigil", c.label2, current = false, onClick = { vm.lock() })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Head(text: String) {
    Text(
        text, style = SigilType.sidebarHead, color = Sigil.colors.label3,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarRow(
    icon: SigilIcon,
    label: String,
    iconTint: Color,
    current: Boolean,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Sigil.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (current) c.fill else Color.Transparent)
            .tap(onClick = onClick)
            .semantics { selected = current }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, iconTint, 17.dp)
        Spacer(Modifier.padding(start = 10.dp))
        Text(
            label, style = SigilType.sidebarItem, color = c.label,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        when {
            trailing != null -> trailing()
            count != null -> Text("$count", style = SigilType.count, color = c.label3)
        }
    }
}
