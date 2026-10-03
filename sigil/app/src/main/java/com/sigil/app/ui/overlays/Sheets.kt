package com.sigil.app.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.titleText
import com.sigil.app.ui.Haptic
import com.sigil.app.ui.LocalPresence
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.ToastMessage
import com.sigil.app.ui.UiState
import com.sigil.app.ui.components.AppIcon
import com.sigil.app.ui.components.Pill
import com.sigil.app.ui.components.SButton
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.colorizedPassword
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.components.tapQuiet
import com.sigil.app.ui.theme.MonoFont
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.GeneratorStyle
import com.sigil.core.PasswordGenerator
import com.sigil.core.Totp

/** A centered glass sheet over a dimmed screen (`.sheet`). Tapping outside cancels. */
@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Sigil.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim).tapQuiet(onDismiss).systemBarsPadding().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().shadow(28.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .background(c.glassStrong).tapQuiet {}.verticalScroll(rememberScrollState()).padding(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(text, style = SigilType.sheetTitle, color = Sigil.colors.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
}

@Composable
private fun InputRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardOptions, style: TextStyle = SigilType.body) {
    val c = Sigil.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SigilType.small, color = c.label2, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = style, color = c.label3, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = style.copy(color = c.label),
                cursorBrush = SolidColor(c.tint), keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val NOTES = mapOf(
    GeneratorStyle.STRONG to "You won't need to remember it. Sigil keeps it for you.",
    GeneratorStyle.MEMORABLE to "Easy to read aloud or type on a TV remote.",
    GeneratorStyle.PIN to "For doors, devices, and cards that only take numbers.",
)

/** New Password: a strong, memorable or PIN secret, ready to save as a login or as a login's new password. */
@Composable
fun GeneratorSheet(changing: Item?, state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    var style by remember { mutableStateOf(GeneratorStyle.STRONG) }
    var mine by remember { mutableStateOf(false) }
    var words by remember { mutableIntStateOf(5) }
    var seed by remember { mutableIntStateOf(0) }
    val generated = remember(style, words, seed) { PasswordGenerator.generate(style, words) }
    var own by remember { mutableStateOf("") }
    var showOwn by remember { mutableStateOf(false) }
    val ownFocus = remember { FocusRequester() }
    val value = if (mine) own else generated.value
    val bits = if (mine) PasswordGenerator.estimateBits(own) else generated.bits
    val strength = PasswordGenerator.describe(bits)
    var site by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }

    LaunchedEffect(mine) { if (mine) ownFocus.requestFocus() }

    Sheet(onDismiss = { vm.closeOverlay() }) {
        SheetTitle(
            when {
                changing == null && mine -> "Save a Password"
                changing == null -> "New Password"
                else -> "New Password for ${changing.titleText()}"
            },
        )
        if (changing == null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(10.dp)).background(c.fill2)) {
                InputRow("Website", site, { site = it }, "example.com", KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                Box(Modifier.fillMaxWidth().padding(start = 12.dp).size(0.5.dp).background(c.sep))
                InputRow("User name", user, { user = it }, "you@example.com", KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)) {
            val options = listOf(GeneratorStyle.STRONG to "Strong", GeneratorStyle.MEMORABLE to "Memorable", GeneratorStyle.PIN to "PIN", null to "Mine")
            for ((s, label) in options) {
                val on = if (s == null) mine else !mine && s == style
                Box(
                    Modifier.weight(1f)
                        .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                        .clip(RoundedCornerShape(7.dp)).background(if (on) c.paper else Color.Transparent)
                        .tap {
                            if (!on) vm.buzz(Haptic.TICK)
                            if (s == null) mine = true else { mine = false; style = s }
                        }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = SigilType.meta, fontWeight = FontWeight.Medium, color = c.label) }
            }
        }
        if (mine) {
            // The password they already have: typed or pasted, hidden by default like any password field.
            val colored = colorizedPassword(own)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(12.dp)).background(c.paper)
                    .border(0.5.dp, c.sep, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (own.isEmpty()) Text("Type or paste it", style = SigilType.generated.copy(fontSize = 18.sp), color = c.label3, maxLines = 1)
                    BasicTextField(
                        value = own,
                        onValueChange = { own = it.replace("\n", "") },
                        singleLine = true,
                        textStyle = SigilType.generated.copy(fontSize = 18.sp, color = c.label),
                        cursorBrush = SolidColor(c.tint),
                        visualTransformation = if (showOwn) VisualTransformation { TransformedText(colored, OffsetMapping.Identity) } else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().focusRequester(ownFocus),
                    )
                }
                Pill(if (showOwn) "Hide" else "Show", { vm.buzz(if (showOwn) Haptic.HIDE else Haptic.REVEAL); showOwn = !showOwn }, SigilIcon.Lock, c.seal)
            }
        } else {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(12.dp)).background(c.paper)
                    .border(0.5.dp, c.sep, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(colorizedPassword(generated.value), style = SigilType.generated, color = c.label, textAlign = TextAlign.Center)
            }
        }
        if (value.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (bits < 40) c.caution else c.good))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${strength.label} · about ${Math.round(bits)} bits · " +
                        if (strength.timeToGuess == "instantly") "guessed instantly" else "would take ${strength.timeToGuess} to guess",
                    style = SigilType.meta, color = c.label2, textAlign = TextAlign.Center,
                )
            }
        }
        if (!mine) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                if (style == GeneratorStyle.MEMORABLE) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(c.fill).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stepper("−", "Fewer words") { vm.buzz(if (words > 3) Haptic.TICK else Haptic.REJECT); words = (words - 1).coerceAtLeast(3) }
                        Text("$words words", style = SigilType.meta, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                        Stepper("+", "More words") { vm.buzz(if (words < 8) Haptic.TICK else Haptic.REJECT); words = (words + 1).coerceAtMost(8) }
                    }
                }
                Pill("Another One", { vm.buzz(Haptic.CLICK); seed++ }, SigilIcon.Refresh)
            }
        }
        Text(
            when {
                !mine -> NOTES.getValue(style)
                own.isNotEmpty() && bits < 40 -> "This one could be guessed. Strong makes one that can't, and Sigil keeps it for you."
                else -> "The password you already use. It's sealed like any other, and Check-up will say if it's reused."
            },
            style = SigilType.meta, color = c.label2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SButton("Cancel", { vm.closeOverlay() }, Modifier.weight(1f))
            SButton(if (mine) "Save Password" else "Use Password", {
                if (changing == null) vm.createLogin(site, user, value) else vm.changePassword(changing.id, value)
            }, Modifier.weight(1f), primary = true, enabled = value.isNotEmpty())
        }
    }
}

@Composable
private fun Stepper(text: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp, 28.dp).clip(RoundedCornerShape(6.dp)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = SigilType.body, color = Sigil.colors.label)
    }
}

/**
 * Turn On Codes: the prototype invents a code secret; a real one comes from the site, so this
 * asks for the setup key the site shows when it offers an authenticator app.
 */
@Composable
fun TotpSetupSheet(item: Item, vm: SigilViewModel) {
    val c = Sigil.colors
    val login = item as? LoginItem ?: return
    var key by remember { mutableStateOf("") }
    val clean = key.uppercase().replace(Regex("[\\s=-]"), "")
    val valid = clean.length >= 16 && clean.all { it in 'A'..'Z' || it in '2'..'7' }
    val preview = if (valid) Totp.code(clean) else null

    Sheet(onDismiss = { vm.closeOverlay() }) {
        SheetTitle("Turn On Codes for ${login.title}")
        Text(
            "In ${login.title}'s security settings, choose to set up an authenticator app. Copy the setup key it shows and paste it here.",
            style = SigilType.small, color = c.label2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(c.fill2)) {
            InputRow(
                "Setup key", key, { key = it }, "JBSW Y3DP EHPK 3PXP",
                KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                style = SigilType.mono,
            )
        }
        Text(
            when {
                preview != null -> "First code: ${preview.take(3)} ${preview.drop(3)}. Enter it on ${login.url.ifBlank { login.title }} to finish."
                key.isNotBlank() -> "That doesn't look like a setup key yet."
                else -> "Codes appear here, ready to copy, whenever you sign in."
            },
            style = SigilType.meta, color = if (preview != null) c.label else c.label2, textAlign = TextAlign.Center,
            fontFamily = if (preview != null) MonoFont else null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SButton("Cancel", { vm.closeOverlay() }, Modifier.weight(1f))
            SButton("Turn On", { vm.enableCodes(login.id, clean) }, Modifier.weight(1f), primary = true, enabled = valid)
        }
    }
}

/** Deleting from Recently Deleted can't be undone, so it asks once, plainly. */
@Composable
fun ConfirmDeleteSheet(ids: Set<String>, state: UiState, vm: SigilViewModel) {
    val c = Sigil.colors
    val n = ids.size
    val one = state.items.firstOrNull { it.id in ids }?.titleText()
    Sheet(onDismiss = { vm.closeOverlay() }) {
        SheetTitle(if (n == 1 && one != null) "Delete “$one” for good?" else "Delete $n items for good?")
        Text(
            "This can't be undone. Anything sealed in ${if (n == 1) "it" else "them"} is erased too.",
            style = SigilType.small, color = c.label2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SButton("Cancel", { vm.closeOverlay() }, Modifier.weight(1f))
            SButton("Delete", { vm.deleteForever(ids) }, Modifier.weight(1f), primary = true, destructive = true)
        }
    }
}

/** "Sigil is locked. Only you can open it." Covers everything until presence is proven. */
@Composable
fun LockScreen(vm: SigilViewModel) {
    val c = Sigil.colors
    val presence = LocalPresence.current
    Box(
        // Mostly opaque even where the content behind is blurred: the lock must hide things on its own.
        Modifier.fillMaxSize().background(c.paper.copy(alpha = if (android.os.Build.VERSION.SDK_INT >= 31) 0.9f else 1f)).tapQuiet {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            AppIcon(64.dp)
            Text("Sigil is locked", style = SigilType.itemTitle.copy(fontSize = SigilType.itemTitle.fontSize * 0.92f), color = c.label, modifier = Modifier.padding(top = 18.dp))
            Text(
                "Only you can open it. Not us, not anyone.", style = SigilType.small, color = c.label2, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp).widthIn(max = 280.dp),
            )
            SButton("Unlock Sigil", { presence.authenticate("Unlock Sigil") { vm.unlocked() } }, primary = true)
        }
    }
}

/** The quiet confirmation pill at the bottom (`.toast`), with an optional action such as Undo. */
@Composable
fun ToastHost(toast: ToastMessage?, vm: SigilViewModel) {
    val c = Sigil.colors
    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(bottom = 18.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        var last by remember { mutableStateOf(toast) }
        if (toast != null) last = toast
        AnimatedVisibility(toast != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            val t = last ?: return@AnimatedVisibility
            Row(
                Modifier.shadow(20.dp, CircleShape).clip(CircleShape).background(c.glassStrong).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SIcon(t.icon, c.seal, 16.dp)
                Text(t.text, style = SigilType.small, color = c.label, modifier = Modifier.weight(1f, fill = false))
                if (t.actionLabel != null) {
                    Text(
                        t.actionLabel, style = SigilType.small, fontWeight = FontWeight.SemiBold, color = c.tint,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).tap { vm.runToastAction() }.padding(4.dp),
                    )
                }
            }
        }
    }
}
