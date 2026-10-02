package com.sigil.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.ui.theme.LocalSigilColors
import com.sigil.app.ui.theme.SigilIcons
import com.sigil.app.ui.theme.tileFor

/** The small rounded-square icon shown next to every list row and at the top of a detail screen. */
@Composable
fun ItemGlyph(item: Item, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val colors = LocalSigilColors.color()
    when (item) {
        is NoteItem -> IconGlyph(SigilIcons.Note, colors.note.copy(alpha = 1f), colors.note.copy(alpha = 0.14f), size, modifier)
        is CardItem -> IconGlyph(SigilIcons.Card, colors.onColor, colors.tiles[5], size, modifier)
        is WifiItem -> IconGlyph(SigilIcons.Wifi, colors.onColor, colors.tiles[3], size, modifier)
        is LoginItem -> MonogramGlyph(item.title, size, modifier)
    }
}

@Composable
private fun IconGlyph(icon: ImageVector, tint: Color, background: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier = modifier.size(size).background(background, RoundedCornerShape((size.value * 0.27f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun MonogramGlyph(title: String, size: Dp, modifier: Modifier) {
    val colors = LocalSigilColors.color()
    val bg = colors.tileFor(title.ifEmpty { "?" })
    Box(
        modifier = modifier.size(size).background(bg, RoundedCornerShape((size.value * 0.27f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "?",
            color = colors.onColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.46f).sp,
        )
    }
}

/** Helper so call sites can write `LocalSigilColors.color()` instead of threading `.current` everywhere. */
@Composable
fun androidx.compose.runtime.ProvidableCompositionLocal<com.sigil.app.ui.theme.SigilColors>.color() = this.current
