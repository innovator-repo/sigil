package com.sigil.app.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sigil.app.data.Repository
import com.sigil.app.data.model.Category
import com.sigil.app.data.model.DraftField
import com.sigil.app.data.model.ItemDraft
import com.sigil.app.data.model.Space
import com.sigil.app.ui.Haptic
import com.sigil.app.ui.Overlay
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.components.Hairline
import com.sigil.app.ui.components.Pill
import com.sigil.app.ui.components.SButton
import com.sigil.app.ui.components.SIcon
import com.sigil.app.ui.components.tap
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.core.GeneratorStyle
import com.sigil.core.PasswordGenerator

fun Category.icon(): SigilIcon = when (this) {
    Category.LOGIN -> SigilIcon.Key
    Category.BANK -> SigilIcon.Bank
    Category.TRADING -> SigilIcon.Chart
    Category.GOVERNMENT -> SigilIcon.IdCard
    Category.EDUCATION -> SigilIcon.Cap
    Category.SIGNATURE -> SigilIcon.Pen
    Category.LICENSE -> SigilIcon.Badge
    Category.LOCK -> SigilIcon.Lock
    Category.OTHER -> SigilIcon.Plus2
}

/** "What are you saving?": a login (with the generator), a kind with its own fields, a card or Wi‑Fi. */
@Composable
fun NewItemSheet(vm: SigilViewModel) {
    val c = Sigil.colors
    Sheet(onDismiss = { vm.closeOverlay() }) {
        SheetTitle("What are you saving?")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.fill2)) {
            var first = true
            fun divider() = if (first) { first = false; false } else true
            for (cat in Category.entries) {
                Choice(cat.icon(), cat.label, cat.hint, divider()) {
                    vm.buzz(Haptic.TICK)
                    if (cat == Category.LOGIN) vm.openGenerator() else vm.openEditor(Overlay.Editor(category = cat))
                }
                if (cat == Category.LOGIN) {
                    Choice(SigilIcon.Card, "Card", "Credit and debit cards", divider()) { vm.buzz(Haptic.TICK); vm.openEditor(Overlay.Editor(card = true)) }
                    Choice(SigilIcon.Wifi, "Wi‑Fi", "Network name and password", divider()) { vm.buzz(Haptic.TICK); vm.openEditor(Overlay.Editor(wifi = true)) }
                }
            }
        }
        SButton("Cancel", { vm.closeOverlay() }, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
}

@Composable
private fun Choice(icon: SigilIcon, title: String, hint: String, divider: Boolean, onClick: () -> Unit) {
    val c = Sigil.colors
    if (divider) Hairline(Modifier.padding(start = 52.dp))
    Row(
        Modifier.fillMaxWidth().tap(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.tintSoft), contentAlignment = Alignment.Center) {
            SIcon(icon, c.tint, 17.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SigilType.rowTitle, color = c.label)
            Text(hint, style = SigilType.rowSub, color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Add or edit any password item. Sealed values are shown as dots until "Show"; every field
 * can be renamed, sealed or unsealed, removed, and new ones added, so any account fits.
 */
@Composable
fun ItemEditorSheet(editor: Overlay.Editor, vm: SigilViewModel) {
    val original by produceState<ItemDraft?>(null, editor) { value = vm.loadDraft(editor) }
    val start = original ?: return
    var draft by remember(start) { mutableStateOf(start) }
    val shown = remember(start) { mutableStateMapOf<String, Boolean>() }
    val c = Sigil.colors
    val isNew = editor.itemId == null

    val heading = when (val d = draft) {
        is ItemDraft.Login -> if (isNew) "New ${d.category.label}" else "Edit ${start.title.ifBlank { d.category.label }}"
        is ItemDraft.Card -> if (isNew) "New Card" else "Edit ${start.title}"
        is ItemDraft.Wifi -> if (isNew) "New Wi‑Fi Network" else "Edit ${start.title}"
    }

    Sheet(onDismiss = { vm.closeOverlay() }) {
        SheetTitle(heading)
        Group {
            Field("Name", draft.title, { v -> draft = draft.withTitle(v) }, placeholder = placeholderFor(draft), divider = false, capitalize = true)
        }
        SpacePicker(draft.space) { s -> vm.buzz(Haptic.TICK); draft = draft.withSpace(s) }

        when (val d = draft) {
            is ItemDraft.Login -> {
                Group {
                    Field(d.category.usernameLabel, d.username, { draft = d.copy(username = it) }, divider = false)
                    SecretField(d.category.passwordLabel, d.password, "pw", shown, { draft = d.copy(password = it) }, generate = d.category != Category.LOCK) {
                        draft = d.copy(password = PasswordGenerator.generate(GeneratorStyle.STRONG).value)
                        shown["pw"] = true
                        vm.buzz(Haptic.CLICK)
                    }
                    Field(if (d.category == Category.EDUCATION) "Link" else "Website", d.url, { draft = d.copy(url = it) }, placeholder = "example.com", keyboard = KeyboardType.Uri)
                }
                if (d.fields.isNotEmpty()) {
                    Group {
                        d.fields.forEachIndexed { i, f ->
                            CustomRow(
                                field = f, divider = i > 0, shown = shown,
                                onChange = { nf -> draft = d.copy(fields = d.fields.map { if (it.id == f.id) nf else it }) },
                                onRemove = { vm.buzz(Haptic.TICK); draft = d.copy(fields = d.fields.filter { it.id != f.id }) },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Pill("Add Field", { vm.buzz(Haptic.TICK); draft = d.copy(fields = d.fields + DraftField(Repository.newId("f"), "", "", false)) }, SigilIcon.Plus2)
                    Pill("Add Sealed Field", { vm.buzz(Haptic.TICK); draft = d.copy(fields = d.fields + DraftField(Repository.newId("f"), "", "", true)) }, SigilIcon.Lock, c.seal)
                }
                Group {
                    Field("Notes", d.notes, { draft = d.copy(notes = it) }, divider = false, singleLine = false, placeholder = "Security questions, recovery codes…", capitalize = true)
                }
            }
            is ItemDraft.Card -> Group {
                Field("Cardholder", d.holder, { draft = d.copy(holder = it) }, divider = false, capitalize = true)
                SecretField("Number", d.number, "number", shown, { draft = d.copy(number = it) }, keyboard = KeyboardType.Number)
                Field("Expires", d.expiry, { draft = d.copy(expiry = it) }, placeholder = "MM/YY", keyboard = KeyboardType.Number)
                SecretField("Security code", d.code, "code", shown, { draft = d.copy(code = it) }, keyboard = KeyboardType.NumberPassword)
            }
            is ItemDraft.Wifi -> Group {
                Field("Network", d.network, { draft = d.copy(network = it) }, divider = false)
                SecretField("Password", d.password, "pw", shown, { draft = d.copy(password = it) })
                Field("Security", d.security, { draft = d.copy(security = it) })
            }
        }

        val canSave = draft.title.isNotBlank()
        Text(
            if (canSave) "Sealed fields need your fingerprint or face to see again." else "Give it a name to save it.",
            style = SigilType.meta, color = c.label2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SButton("Cancel", { vm.closeOverlay() }, Modifier.weight(1f))
            SButton("Save", { vm.saveDraft(editor, draft, if (isNew) null else start) }, Modifier.weight(1f), primary = true, enabled = canSave)
        }
    }
}

private fun placeholderFor(d: ItemDraft): String = when (d) {
    is ItemDraft.Card -> "HDFC Millennia"
    is ItemDraft.Wifi -> "Home Wi‑Fi"
    is ItemDraft.Login -> when (d.category) {
        Category.BANK -> "SBI, HDFC Bank…"
        Category.TRADING -> "Dhan, Groww…"
        Category.GOVERNMENT -> "EPFO UAN, Income Tax…"
        Category.EDUCATION -> "Chandigarh University"
        Category.SIGNATURE -> "eMudhra"
        Category.LICENSE -> "Internet Download Manager"
        Category.LOCK -> "Front door, bike lock…"
        else -> "Google (work), IRCTC…"
    }
}

private fun ItemDraft.withTitle(v: String): ItemDraft = when (this) {
    is ItemDraft.Login -> copy(title = v)
    is ItemDraft.Card -> copy(title = v)
    is ItemDraft.Wifi -> copy(title = v)
}

private fun ItemDraft.withSpace(s: Space): ItemDraft = when (this) {
    is ItemDraft.Login -> copy(space = s)
    is ItemDraft.Card -> copy(space = s)
    is ItemDraft.Wifi -> copy(space = s)
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(Sigil.colors.fill2), content = content)
}

@Composable
private fun SpacePicker(current: Space, onPick: (Space) -> Unit) {
    val c = Sigil.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)) {
        for (s in Space.entries) {
            val on = s == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (on) c.paper else Color.Transparent).tap { onPick(s) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s.label, style = SigilType.meta, fontWeight = FontWeight.Medium, color = c.label) }
        }
    }
}

@Composable
private fun Input(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SigilType.body,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
    hidden: Boolean = false,
) {
    val c = Sigil.colors
    Box(modifier) {
        if (value.isEmpty()) Text(placeholder, style = style, color = c.label3, maxLines = 1)
        BasicTextField(
            value, onChange, singleLine = singleLine, textStyle = style.copy(color = c.label),
            cursorBrush = SolidColor(c.tint),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (hidden && keyboard == KeyboardType.Text) KeyboardType.Password else keyboard,
                autoCorrectEnabled = capitalize && !hidden,
                capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 0.dp else 44.dp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    divider: Boolean = true,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
) {
    if (divider) Hairline(Modifier.padding(start = 12.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = SigilType.fieldLabel, color = Sigil.colors.label2)
        Input(value, onChange, placeholder, Modifier.fillMaxWidth().padding(top = 3.dp), keyboard = keyboard, singleLine = singleLine, capitalize = capitalize)
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    key: String,
    shown: MutableMap<String, Boolean>,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    generate: Boolean = false,
    onGenerate: () -> Unit = {},
) {
    val c = Sigil.colors
    val visible = shown[key] == true
    Hairline(Modifier.padding(start = 12.dp))
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(SigilIcon.Lock, c.seal, 11.dp, stroke = 2.2f)
            Spacer(Modifier.width(4.dp))
            Text(label, style = SigilType.fieldLabel, color = c.label2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Input(value, onChange, "", Modifier.weight(1f).padding(top = 3.dp), style = SigilType.password, keyboard = keyboard, hidden = !visible)
            if (generate) Pill("Generate", onGenerate, SigilIcon.Refresh, modifier = Modifier.padding(end = 6.dp))
            Pill(if (visible) "Hide" else "Show", { shown[key] = !visible }, color = c.seal)
        }
    }
}

/** A field of the person's own: rename it, seal or unseal it, or remove it. */
@Composable
private fun CustomRow(
    field: DraftField,
    divider: Boolean,
    shown: MutableMap<String, Boolean>,
    onChange: (DraftField) -> Unit,
    onRemove: () -> Unit,
) {
    val c = Sigil.colors
    val visible = !field.secret || shown[field.id] == true
    if (divider) Hairline(Modifier.padding(start = 12.dp))
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (field.secret) {
                SIcon(SigilIcon.Lock, c.seal, 11.dp, stroke = 2.2f)
                Spacer(Modifier.width(4.dp))
            }
            Input(field.label, { onChange(field.copy(label = it)) }, "Field name", Modifier.weight(1f), style = SigilType.fieldLabel, capitalize = true)
            Text(
                if (field.secret) "Unseal" else "Seal", style = SigilType.meta, color = c.seal,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).tap { onChange(field.copy(secret = !field.secret)) }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).tap(onClick = onRemove), contentAlignment = Alignment.Center) {
                SIcon(SigilIcon.Close, c.label3, 14.dp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Input(
                field.value, { onChange(field.copy(value = it)) }, "Value", Modifier.weight(1f).padding(top = 2.dp),
                style = if (field.secret) SigilType.password else SigilType.body, hidden = !visible,
            )
            if (field.secret) Pill(if (visible) "Hide" else "Show", { shown[field.id] = !visible }, color = c.seal)
        }
    }
}
