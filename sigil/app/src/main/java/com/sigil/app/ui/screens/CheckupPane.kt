package com.sigil.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.titleText
import com.sigil.app.logic.SecurityCheckup
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.WindowMode
import com.sigil.app.ui.components.BackButton
import com.sigil.app.ui.components.ItemGlyph
import com.sigil.app.ui.components.Ring
import com.sigil.app.ui.components.SButton
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.app.ui.theme.UiFont

/** "Calm security": one issue at a time, why it matters, and its fix right there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckupPane(state: UiState, vm: SigilViewModel, mode: WindowMode, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val r = state.checkup
    val open = r.open
    val fixedN = state.fixed.size
    val all = fixedN + open.size
    val pct by animateFloatAsState(if (all == 0) 1f else fixedN.toFloat() / all, label = "checkup")
    val title = when (open.size) {
        0 -> "You're all set."
        1 -> "One thing is worth a minute."
        else -> "${open.size} things are worth a minute."
    }

    Column(modifier.background(c.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 4.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mode == WindowMode.COMPACT) BackButton("Library") { vm.showSidebar() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Page(mode) {
                Row(Modifier.padding(top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ring(pct, c.good, 64.dp, 6.dp)
                    Spacer(Modifier.padding(start = 18.dp))
                    Column {
                        Text(title, style = SigilType.itemTitle, color = c.label)
                        Text(
                            "${r.healthy} of ${r.total} passwords look good." + if (open.isNotEmpty()) " Start at the top; it matters most." else "",
                            style = SigilType.small, color = c.label2, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                val next = open.firstOrNull()
                if (next != null) {
                    val copy = SecurityCheckup.describe(next)
                    Column(
                        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = c.sep, spotColor = c.sep)
                            .clip(RoundedCornerShape(16.dp)).background(c.paper).padding(20.dp),
                    ) {
                        Kicker("Up next")
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ItemGlyph(next.item, 44.dp)
                            Column {
                                Text(copy.kind, style = SigilType.meta, fontWeight = FontWeight.SemiBold, color = if (copy.soft) c.tint else c.caution)
                                Text(next.item.title, fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = c.label, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                                Text(copy.why, style = SigilType.body, color = c.label, modifier = Modifier.padding(bottom = 6.dp))
                                Text(copy.fix, style = SigilType.small, color = c.label2)
                                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SButton(copy.action, { vm.fix(next) }, primary = true)
                                    SButton("Not Now", { vm.snooze(next) })
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(c.good.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            SIcon(SigilIcon.Check, c.good, 30.dp, stroke = 2.2f)
                        }
                        Text("Nothing needs your attention.", fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = c.label, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        Text(
                            "Sigil keeps checking quietly and will only speak up when something's worth a minute.",
                            style = SigilType.small, color = c.label2, textAlign = TextAlign.Center,
                        )
                    }
                }

                if (open.size > 1) {
                    ListHead("After that")
                    open.drop(1).forEach { x -> CheckRow(x.item, SecurityCheckup.describe(x).kind, done = false) }
                }
                if (fixedN > 0) {
                    ListHead("Done")
                    state.fixed.forEach { f -> CheckRow(f.item, f.what, done = true) }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text.uppercase(), style = SigilType.kicker, color = Sigil.colors.label3, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun ListHead(text: String) {
    Text(text.uppercase(), style = SigilType.kicker, color = Sigil.colors.label3, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
}

@Composable
private fun CheckRow(item: Item, what: String, done: Boolean) {
    val c = Sigil.colors
    val sep = c.sep
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(sep, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx()) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ItemGlyph(item, 26.dp)
        Text(item.titleText(), style = SigilType.small, color = c.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(what, style = SigilType.small, color = c.label2)
        if (done) SIcon(SigilIcon.Check, c.good, 16.dp)
    }
}
