package com.sigil.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.data.model.titleText
import com.sigil.app.logic.IssueType
import com.sigil.app.logic.SecurityCheckup
import com.sigil.app.ui.LocalPresence
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.components.FieldAction
import com.sigil.app.ui.components.FieldGroup
import com.sigil.app.ui.components.FieldRow
import com.sigil.app.ui.components.IconTile
import com.sigil.app.ui.components.ItemGlyph
import com.sigil.app.ui.components.Pill
import com.sigil.app.ui.components.Ring
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.colorizedPassword
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.theme.MonoFont
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.app.ui.theme.UiFont
import com.sigil.core.Totp
import kotlinx.coroutines.delay

@Composable
private fun Head(item: Item, glyph: @Composable () -> Unit) {
    Row(Modifier.padding(top = 8.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        glyph()
        Spacer(Modifier.padding(start = 14.dp))
        Column {
            Text(item.titleText(), style = SigilType.itemTitle, color = Sigil.colors.label)
            Text(editedLine(item), style = SigilType.meta, color = Sigil.colors.label2, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun PlainRow(label: String, value: String, divider: Boolean, vm: SigilViewModel, copy: Boolean = true) {
    FieldRow(
        label, divider,
        value = { Text(value, style = SigilType.fieldValue, color = Sigil.colors.label) },
        actions = { if (copy) FieldAction(SigilIcon.Copy, "Copy $label") { vm.copyValue(value, label, sensitive = false) } },
    )
}

/**
 * A sealed field: masked until revealed with presence, then shown for 30 seconds with a
 * draining bar underneath, and copyable (which also asks for presence).
 */
@Composable
private fun SecretRow(
    item: Item, key: String, label: String, secretId: String, state: UiState, vm: SigilViewModel,
    divider: Boolean, colorize: Boolean = true, copyLabel: String = label,
) {
    val c = Sigil.colors
    val presence = LocalPresence.current
    val fullKey = "${item.id}:$key"
    val shown = state.revealed[fullKey]
    val length by produceState(12, secretId) { value = vm.secretLength(secretId) }
    FieldRow(
        label, divider,
        value = {
            if (shown != null) {
                Text(
                    if (colorize) colorizedPassword(shown.value) else androidx.compose.ui.text.AnnotatedString(shown.value),
                    style = if (colorize) SigilType.password else SigilType.mono, color = c.label,
                )
            } else {
                Text("•".repeat(length.coerceIn(6, 12)), style = SigilType.fieldValue, color = c.label2, letterSpacing = 2.sp)
            }
        },
        actions = {
            if (shown != null) {
                Pill("Hide", { vm.hide(fullKey) }, SigilIcon.Lock, c.seal)
            } else {
                Pill("Reveal", { presence.require("Reveal ${label.lowercase()}") { vm.reveal(fullKey, secretId) } }, SigilIcon.Lock, c.seal)
            }
            FieldAction(SigilIcon.Copy, "Copy $label") {
                presence.require("Copy ${copyLabel.lowercase()}") { vm.copySecret(secretId, copyLabel) }
            }
        },
        below = {
            if (shown != null) {
                val total = SigilViewModel.REVEAL_MS.toFloat()
                var target by remember(shown) { mutableFloatStateOf(((shown.until - System.currentTimeMillis()) / total).coerceIn(0f, 1f)) }
                val left = remember(shown) { (shown.until - System.currentTimeMillis()).coerceAtLeast(0) }
                val fraction by animateFloatAsState(target, tween(left.toInt(), easing = LinearEasing), label = "hides")
                LaunchedEffect(shown) { target = 0f }
                Box(Modifier.padding(top = 6.dp, end = 4.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(c.fill)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(c.seal))
                }
            }
        },
    )
}

@Composable
fun LoginBody(item: LoginItem, state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    val context = LocalContext.current
    Head(item) { ItemGlyph(item, 56.dp) }

    val issues = state.checkup.open.filter { it.item.id == item.id }
    val issue = issues.firstOrNull { it.type != IssueType.NO_2FA } ?: issues.firstOrNull()
    if (issue != null) {
        val copy = SecurityCheckup.describe(issue)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(12.dp)).background(c.cautionSoft).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SIcon(SigilIcon.Warn, c.caution, 20.dp)
            Column {
                Text(copy.kind, style = SigilType.body, fontWeight = FontWeight.SemiBold, color = c.label)
                Text(copy.why, style = SigilType.small, color = c.label, modifier = Modifier.padding(top = 2.dp))
                Pill("${copy.action}…", { vm.fix(issue) }, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }

    FieldGroup {
        PlainRow("User name", item.username, divider = false, vm = vm)
        SecretRow(item, "pw", "Password", item.passwordSecretId, state, vm, divider = true)
        item.totpSecretId?.let { TotpRow(it, vm) }
        if (item.hasPasskey) {
            FieldRow(
                "Passkey", divider = true,
                value = {
                    SIcon(SigilIcon.Passkey, c.good, 16.dp)
                    Spacer(Modifier.padding(start = 6.dp))
                    Text("Sign in with a passkey", style = SigilType.small, fontWeight = FontWeight.Medium, color = c.good)
                },
            )
        }
    }
    if (item.url.isNotBlank()) {
        FieldGroup {
            FieldRow(
                "Website", divider = false,
                value = {
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).tap {
                            val uri = Uri.parse(if (item.url.contains("://")) item.url else "https://${item.url}")
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: ActivityNotFoundException) {
                                vm.toast("No browser to open ${item.url}.", SigilIcon.External)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.url, style = SigilType.fieldValue, color = c.tint)
                        Spacer(Modifier.padding(start = 3.dp))
                        SIcon(SigilIcon.External, c.tint, 15.dp)
                    }
                },
            )
        }
    }
    FieldGroup {
        FieldRow(
            "Password", divider = false,
            value = { Pill("Change Password…", { vm.openGenerator(item.id) }, SigilIcon.Refresh) },
        )
    }
}

/** The current verification code with its 30-second ring; the ring turns orange for the last 5 seconds. */
@Composable
private fun TotpRow(secretId: String, vm: SigilViewModel) {
    val c = Sigil.colors
    val seed by produceState<String?>(null, secretId) { value = vm.secretValue(secretId) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    val code = seed?.let { Totp.code(it, now) }
    val left = Totp.secondsRemaining(now)
    FieldRow(
        "Verification code", divider = true,
        value = {
            Text(
                code?.let { "${it.take(3)} ${it.drop(3)}" } ?: "••• •••",
                style = SigilType.code, color = c.label,
            )
        },
        actions = {
            Ring(left / 30f, if (left <= 5) c.caution else c.tint, 22.dp, 3.dp, Modifier.padding(end = 6.dp))
            FieldAction(SigilIcon.Copy, "Copy code") { code?.let { vm.copyValue(it, "Code") } }
        },
    )
}

@Composable
fun CardBody(item: CardItem, state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    Column(
        Modifier
            .padding(bottom = 22.dp)
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = c.cardB, spotColor = c.cardB)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(c.cardA, c.cardB)))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onColor, modifier = Modifier.weight(1f))
            SIcon(SigilIcon.Card, c.onColor, 16.dp)
        }
        Text("•••• •••• •••• ${item.last4}", fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = 0.08.em, color = c.onColor)
        Row {
            Text(item.holder.uppercase(), fontFamily = UiFont, fontSize = 11.sp, letterSpacing = 0.08.em, color = c.onColor.copy(alpha = 0.86f), modifier = Modifier.weight(1f))
            Text(item.expiry, fontFamily = UiFont, fontSize = 11.sp, letterSpacing = 0.08.em, color = c.onColor.copy(alpha = 0.86f))
        }
    }
    Head(item) { IconTile(SigilIcon.Card, c.tiles[5], 56.dp) }
    FieldGroup {
        PlainRow("Cardholder", item.holder, divider = false, vm = vm)
        SecretRow(item, "number", "Number", item.numberSecretId, state, vm, divider = true, colorize = false, copyLabel = "Card number")
        PlainRow("Expires", item.expiry, divider = true, vm = vm, copy = false)
        SecretRow(item, "code", "Security code", item.codeSecretId, state, vm, divider = true, colorize = false)
    }
}

@Composable
fun WifiBody(item: WifiItem, state: UiState, vm: SigilViewModel) {
    Head(item) { IconTile(SigilIcon.Wifi, Sigil.colors.tiles[3], 56.dp) }
    FieldGroup {
        PlainRow("Network", item.network, divider = false, vm = vm)
        SecretRow(item, "pw", "Password", item.passwordSecretId, state, vm, divider = true, copyLabel = "Wi‑Fi password")
        PlainRow("Security", item.security, divider = true, vm = vm, copy = false)
    }
}
