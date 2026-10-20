package com.sigil.app.designsystem

import androidx.compose.ui.text.font.FontFamily
import com.sigil.app.ui.theme.MonoFont
import com.sigil.app.ui.theme.ReadFont
import com.sigil.app.ui.theme.UiFont

/** The Lume voices Sigil uses: Geist for interface, Geist Mono for literal values, Newsreader for writing. */
object LumeType {
    val ui: FontFamily = UiFont
    /** Passwords, one-time codes, card numbers. */
    val mono: FontFamily = MonoFont
    /** Notes and the diary. */
    val read: FontFamily = ReadFont
}
