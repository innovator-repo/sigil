package com.sigil.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sigil.app.R

/**
 * The prototype's font stack falls back to Geist, Newsreader and Geist Mono wherever the
 * Apple system faces don't exist (which is what its Android demo showed), so Sigil bundles
 * those three (SIL Open Font License) to look the same on every device.
 */
val UiFont = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** --font-read: the serif "paper" face, used only for what people write. */
val ReadFont = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_bold, FontWeight.Bold),
)

/** --font-mono: passwords, codes and other literal values. */
val MonoFont = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

/** Text styles taken from the prototype's CSS at its phone breakpoint. */
object SigilType {
    val largeTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.01).em, lineHeight = 36.sp)
    val listTitleWide = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.01).em)
    val group = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val sidebarHead = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val sidebarItem = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val count = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val rowTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val rowSub = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val back = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val meta = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val fieldLabel = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val fieldValue = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val itemTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.015).em, lineHeight = 28.sp)
    val pill = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val button = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val body = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val kicker = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.06.em)
    val sheetTitle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val password = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.02.em)
    val mono = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val code = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 0.06.em)
    val generated = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp)
    val noteTitle = TextStyle(fontFamily = ReadFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.01).em)
    val noteBody = TextStyle(fontFamily = ReadFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp)
    val notesField = TextStyle(fontFamily = ReadFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val searchInput = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 20.sp)
}

val SigilTypography = Typography(
    bodyLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)
