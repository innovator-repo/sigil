package com.sigil.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilIcon
import com.sigil.app.ui.theme.SigilType
import com.sigil.app.ui.theme.UiFont
import com.sigil.app.ui.theme.tileFor
import com.sigil.core.CharClass
import com.sigil.core.PasswordGenerator

@Composable
fun SIcon(icon: SigilIcon, tint: Color, size: Dp, modifier: Modifier = Modifier, stroke: Float = 1.8f) {
    Icon(icon.vector(stroke), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Clickable with the platform ripple, clipped to [shape]-like rounding by the caller. */
@Composable
fun Modifier.tap(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(),
        enabled = enabled,
        role = role,
        onClick = onClick,
    )

/** Clickable with no visual feedback, for scrims and other surfaces that aren't buttons. */
@Composable
fun Modifier.tapQuiet(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/** The rounded-square glyph beside every row: amber page for notes, colored tile for passwords. */
@Composable
fun ItemGlyph(item: Item, size: Dp = 30.dp) {
    val c = Sigil.colors
    val radius = RoundedCornerShape(size * 0.27f)
    when (item) {
        is NoteItem -> Box(
            Modifier.size(size).clip(radius).background(c.note.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { SIcon(SigilIcon.Note, c.note, size * 0.57f) }
        is CardItem -> IconTile(SigilIcon.Card, c.tiles[5], size)
        is WifiItem -> IconTile(SigilIcon.Wifi, c.tiles[3], size)
        is LoginItem -> Box(
            Modifier.size(size).clip(radius).background(c.tileFor(item.title)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.title.trim().take(1).uppercase().ifEmpty { "?" },
                color = c.onColor,
                fontFamily = UiFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.47f).sp,
            )
        }
    }
}

@Composable
fun IconTile(icon: SigilIcon, color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(color),
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, Sigil.colors.onColor, size * 0.53f, stroke = 2f) }
}

/** A small icon button in a toolbar (`.tbtn`). */
@Composable
fun ToolButton(icon: SigilIcon, description: String, onClick: () -> Unit, tint: Color = Sigil.colors.label2) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.vector(1.8f), contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/** "‹ Library": the phone's navigation-stack back button (`.back`). */
@Composable
fun BackButton(label: String, onClick: () -> Unit) {
    val c = Sigil.colors
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).tap(onClick = onClick).padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(SigilIcon.Back, c.tint, 21.dp, stroke = 2.2f)
        Spacer(Modifier.width(2.dp))
        Text(label, style = SigilType.back, color = c.tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Rounded capsule action (`.pill`), tinted blue, or indigo for anything sealed. */
@Composable
fun Pill(text: String, onClick: () -> Unit, icon: SigilIcon? = null, color: Color = Sigil.colors.tint, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Sigil.colors.fill).tap(onClick = onClick).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) SIcon(icon, color, 13.dp, stroke = 2.2f)
        Text(text, style = SigilType.pill, color = color, maxLines = 1)
    }
}

/** Rectangular button (`.btn` / `.btn.primary`). */
@Composable
fun SButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val c = Sigil.colors
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) c.tint.copy(alpha = if (enabled) 1f else 0.4f) else c.fill)
            .tap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SigilType.button, color = if (primary) c.onColor else c.label, maxLines = 1)
    }
}

/** An inset grouped list (`.group`): rows on a soft fill, separated by hairlines. */
@Composable
fun FieldGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(12.dp)).background(Sigil.colors.fill2),
        content = content,
    )
}

/**
 * One field (`.frow`) at the phone breakpoint: the label sits above the value, actions on the
 * right. [divider] draws the hairline separating it from the row above.
 */
@Composable
fun FieldRow(
    label: String,
    divider: Boolean,
    value: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val sep = Sigil.colors.sep
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider) drawLine(sep, Offset(14.dp.toPx(), 0f), Offset(size.width, 0f), 0.5.dp.toPx())
            }
            .heightIn(min = 44.dp)
            .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Text(label, style = SigilType.fieldLabel, color = Sigil.colors.label2)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = value)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), content = actions)
        }
        below()
    }
}

/** Small square icon action inside a field row (`.ibtn`), e.g. copy. */
@Composable
fun FieldAction(icon: SigilIcon, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.vector(1.8f), contentDescription = description, tint = Sigil.colors.label2, modifier = Modifier.size(17.dp))
    }
}

/** Group heading in lists and the sidebar (`.grp`, `.sb-head`). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SigilType.group, color = Sigil.colors.label3, modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp))
}

/** A circular progress ring, drawn clockwise from 12 o'clock (`.ring`, `.cu-ring`). */
@Composable
fun Ring(progress: Float, color: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    val track = Sigil.colors.fill
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        drawCircle(track, radius = (this.size.minDimension - w) / 2, style = Stroke(w))
        drawArc(
            color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

/** Passwords show digits in blue and symbols in orange, so 0/O and 1/l/I can't be confused. */
@Composable
fun colorizedPassword(value: String): AnnotatedString {
    val c = Sigil.colors
    return buildAnnotatedString {
        for (ch in PasswordGenerator.colorize(value)) when (ch.charClass) {
            CharClass.DIGIT -> withStyle(SpanStyle(color = c.digit)) { append(ch.char) }
            CharClass.SYMBOL -> withStyle(SpanStyle(color = c.symbol)) { append(ch.char) }
            CharClass.PLAIN -> append(ch.char)
        }
    }
}

/** The app mark on its indigo tile, as on the lock screen. */
@Composable
fun AppIcon(size: Dp) {
    val c = Sigil.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(Brush.verticalGradient(listOf(c.seal, c.cardA))),
        contentAlignment = Alignment.Center,
    ) { SIcon(SigilIcon.AppMark, c.onColor, size * 0.56f, stroke = 2f) }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(0.5.dp).background(Sigil.colors.sep))
}
