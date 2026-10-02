package com.sigil.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every semantic color token from the Kept design system's CSS variables, carried over
 * verbatim (light + dark) so Sigil reads as the same app. Exposed via [LocalSigilColors]
 * rather than squeezed into Material3's limited role set, which has no room for things
 * like a "seal" tint or eight tile colors.
 */
data class SigilColors(
    val bg: Color,
    val paper: Color,
    val label: Color,
    val label2: Color,
    val label3: Color,
    val sep: Color,
    val fill: Color,
    val fill2: Color,
    val tint: Color,
    val tintSoft: Color,
    val seal: Color,
    val sealSoft: Color,
    val note: Color,
    val good: Color,
    val caution: Color,
    val cautionSoft: Color,
    val critical: Color,
    val digit: Color,
    val symbol: Color,
    val glass: Color,
    val glassStrong: Color,
    val scrim: Color,
    val onColor: Color,
    val cardA: Color,
    val cardB: Color,
    val wall1: Color,
    val wall2: Color,
    val wall3: Color,
    val wall4: Color,
    val tiles: List<Color>,
)

val LightSigilColors = SigilColors(
    bg = Color(0xFFF5F5F7),
    paper = Color(0xFFFFFFFF),
    label = Color(0xFF1D1D1F),
    label2 = Color(0xFF6E6E73),
    label3 = Color(0xFF8A8A8F),
    sep = Color(0x243C3C43),
    fill = Color(0x1F787880),
    fill2 = Color(0x12787880),
    tint = Color(0xFF0071E3),
    tintSoft = Color(0x1A0071E3),
    seal = Color(0xFF5856D6),
    sealSoft = Color(0x1C5856D6),
    note = Color(0xFFC98A00),
    good = Color(0xFF248A3D),
    caution = Color(0xFFC93400),
    cautionSoft = Color(0x14C93400),
    critical = Color(0xFFD70015),
    digit = Color(0xFF0066CC),
    symbol = Color(0xFFC93400),
    glass = Color(0xBDF2F2F6),
    glassStrong = Color(0xDBFCFCFD),
    scrim = Color(0x2E000000),
    onColor = Color(0xFFFFFFFF),
    cardA = Color(0xFF2B2D42),
    cardB = Color(0xFF4B3F8C),
    wall1 = Color(0xFFF4C3A1),
    wall2 = Color(0xFFD2B8EC),
    wall3 = Color(0xFF93B6EC),
    wall4 = Color(0xFFF7E3D2),
    tiles = listOf(
        Color(0xFFE0584A), Color(0xFFD9822B), Color(0xFF3F9E66), Color(0xFF2E9696),
        Color(0xFF3478F6), Color(0xFF5E5CE6), Color(0xFFA259D9), Color(0xFF6B7A8F),
    ),
)

val DarkSigilColors = SigilColors(
    bg = Color(0xFF000000),
    paper = Color(0xFF1C1C1E),
    label = Color(0xFFF5F5F7),
    label2 = Color(0xFFA1A1A6),
    label3 = Color(0xFF8E8E93),
    sep = Color(0x1FFFFFFF),
    fill = Color(0x42787880),
    fill2 = Color(0x24787880),
    tint = Color(0xFF2997FF),
    tintSoft = Color(0x292997FF),
    seal = Color(0xFF8E8BFF),
    sealSoft = Color(0x2E7D7AFF),
    note = Color(0xFFFFD60A),
    good = Color(0xFF30D158),
    caution = Color(0xFFFF9F0A),
    cautionSoft = Color(0x1FFF9F0A),
    critical = Color(0xFFFF453A),
    digit = Color(0xFF5AB0FF),
    symbol = Color(0xFFFF9F0A),
    glass = Color(0x9E28282C),
    glassStrong = Color(0xDB222226),
    scrim = Color(0x73000000),
    onColor = Color(0xFFFFFFFF),
    cardA = Color(0xFF1E2033),
    cardB = Color(0xFF3F3590),
    wall1 = Color(0xFF4A2A4F),
    wall2 = Color(0xFF25306A),
    wall3 = Color(0xFF0F4257),
    wall4 = Color(0xFF1A1633),
    tiles = listOf(
        Color(0xFFE0584A), Color(0xFFD9822B), Color(0xFF3F9E66), Color(0xFF2E9696),
        Color(0xFF3478F6), Color(0xFF5E5CE6), Color(0xFFA259D9), Color(0xFF6B7A8F),
    ),
)

val LocalSigilColors = staticCompositionLocalOf { DarkSigilColors }

/** Deterministic tile color for an item's monogram glyph, matching the web app's tileFor(). */
fun SigilColors.tileFor(seed: String): Color {
    var h = 0
    for (c in seed) h = (h * 31 + c.code) and 0x7FFFFFFF
    return tiles[h % tiles.size]
}
