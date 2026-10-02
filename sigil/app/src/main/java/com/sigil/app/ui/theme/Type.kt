package com.sigil.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** --font-ui: the sans system face used for chrome (sidebar, list rows, buttons, fields). */
val UiFont = FontFamily.SansSerif

/** --font-read: the serif face used only inside the note editor (title + body). */
val ReadFont = FontFamily.Serif

/** --font-mono: used for passwords, verification codes, and other literal values. */
val MonoFont = FontFamily.Monospace

/** Hand-picked text styles mirroring the CSS rules that don't map onto Material3's named slots. */
object SigilType {
    val appTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp)
    val listTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp)
    val sectionHead = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.3.sp)
    val rowTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val rowSub = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val fieldLabel = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val fieldValue = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val itemTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp)
    val meta = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val pill = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val mono = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.3.sp)
    val monoLarge = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 1.sp)
    val noteTitle = TextStyle(fontFamily = ReadFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.2).sp)
    val noteBody = TextStyle(fontFamily = ReadFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp)
    val sealChip = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
}

val SigilTypography = Typography(
    bodyLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)
