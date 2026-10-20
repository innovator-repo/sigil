package com.sigil.app.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import com.sigil.app.ui.theme.SigilColors
import java.time.LocalTime

/** Sigil's accents, as named in the Lume palette table. Light / dark pairs. */
object LumePalette {
    val TrustSapphireLight = Color(0xFF0071E3)
    val TrustSapphireDark = Color(0xFF2997FF)
    val SealIndigoLight = Color(0xFF5856D6)
    val SealIndigoDark = Color(0xFF8E8BFF)
    val ArchiveAmberLight = Color(0xFFC98A00)
    val ArchiveAmberDark = Color(0xFFFFD60A)
}

/** The four parts of the day, matching Kasane's blocks and Vow's night mode. */
enum class CircadianPhase {
    /** 06:00-11:59, warm washi. */
    MORNING,
    /** 12:00-19:59, plain paper. */
    DAY,
    /** 20:00-21:59, warm graphite. */
    EVENING,
    /** 22:00-05:59, quiet obsidian. */
    SLEEP;

    companion object {
        /** Tests set this to pin the surfaces, so screenshots don't change with the clock. */
        @Volatile var override: CircadianPhase? = null

        fun now(): CircadianPhase = override ?: at(LocalTime.now())

        fun at(time: LocalTime): CircadianPhase = when (time.hour) {
            in 6..11 -> MORNING
            in 12..19 -> DAY
            in 20..21 -> EVENING
            else -> SLEEP
        }
    }
}

private val Washi = Color(0xFFFAF9F6)
private val WarmGraphite = Color(0xFF181715)
private val WarmGraphitePaper = Color(0xFF211F1D)
private val Obsidian = Color(0xFF0B0F14)
private val ObsidianPaper = Color(0xFF121820)

/**
 * Re-surfaces [base] for the time of day. Evening and sleep are always dark, whatever the
 * system theme, and sleep drops the amber warning colour so nothing glows at night. Security
 * critical red is kept, and every accent keeps its meaning.
 */
fun circadianColors(base: SigilColors, dark: SigilColors, phase: CircadianPhase): SigilColors = when (phase) {
    CircadianPhase.MORNING -> if (base === dark) base else base.copy(bg = Washi)
    CircadianPhase.DAY -> base
    CircadianPhase.EVENING -> dark.copy(bg = WarmGraphite, paper = WarmGraphitePaper)
    CircadianPhase.SLEEP -> dark.copy(
        bg = Obsidian,
        paper = ObsidianPaper,
        caution = dark.label2,
        cautionSoft = dark.fill2,
        symbol = dark.label2,
    )
}

/** The current phase, re-read whenever the screen resumes and at each phase boundary. */
@Composable
fun rememberCircadianPhase(): CircadianPhase {
    var phase by remember { mutableStateOf(CircadianPhase.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            phase = CircadianPhase.now()
            delay(60_000)
        }
    }
    return phase
}
