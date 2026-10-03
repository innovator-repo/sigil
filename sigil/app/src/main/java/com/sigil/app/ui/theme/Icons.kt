package com.sigil.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The prototype's icon set, ported path-for-path from its inline SVGs (24x24 viewBox,
 * round caps and joins, no fill) so Sigil's icons are the same drawings, not look-alikes.
 * Icons are tinted by [androidx.compose.material3.Icon] like any other vector.
 */
enum class SigilIcon(vararg val parts: String) {
    Search(circle(11f, 11f, 6.5f), "M20 20l-4.3-4.3"),
    All(rect(4f, 7.5f, 12.5f, 12.5f, 3f), "M8 4.5h8.5a3 3 0 0 1 3 3V16"),
    Note(
        "M6.5 3.5h7.5l4 4v12.5a.5.5 0 0 1-.5.5h-11a.5.5 0 0 1-.5-.5V4a.5.5 0 0 1 .5-.5z",
        "M9 11.5h6M9 15h6M9 18.5h3.5",
    ),
    Key(circle(8f, 15.5f, 4f), "M10.9 12.6L19 4.5M16 7.5l2.5 2.5M13.8 9.7l2 2"),
    Pin("M9 3.5h6l-.8 5.2 3.3 3.3H6.5l3.3-3.3z", "M12 12v8.5"),
    Unpin("M9 3.5h6l-.8 5.2 3.3 3.3H6.5l3.3-3.3z", "M12 12v8.5M4 4l16 16"),
    Person(circle(12f, 8f, 3.5f), "M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5"),
    People(
        circle(9f, 8.5f, 3f),
        "M3.5 19c.6-3 2.8-4.7 5.5-4.7s4.9 1.7 5.5 4.7",
        circle(16.5f, 9.5f, 2.5f),
        "M16.2 14.4c2.3 0 3.9 1.4 4.3 4",
    ),
    Briefcase(rect(3.5f, 7.5f, 17f, 12f, 2.5f), "M9 7.5V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1.5M3.5 12.5h17"),
    Shield("M12 3.5l7 2.8v5.4c0 4.3-2.9 7.6-7 8.8-4.1-1.2-7-4.5-7-8.8V6.3z", "M9 12l2 2 4-4"),
    Trash("M4.5 7h15M9.5 7V5h5v2M6.5 7l1 12.5h9l1-12.5"),
    Lock(rect(5f, 10.5f, 14f, 10f, 2.5f), "M8 10.5V7.5a4 4 0 0 1 8 0v3"),
    Compose(
        "M12.5 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-6",
        "M18.3 3.7a1.7 1.7 0 0 1 2.4 2.4l-8.2 8.2-3 .7.7-3z",
    ),
    Copy(
        rect(8.5f, 8.5f, 11f, 11f, 2.5f),
        "M15.5 8.5V6a1.5 1.5 0 0 0-1.5-1.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5",
    ),
    Refresh("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5v4h-4"),
    Back("M15 5l-7 7 7 7"),
    Checklist("M4 6.5l1.5 1.5 3-3M4 14.5l1.5 1.5 3-3M11.5 7h8.5M11.5 15h8.5"),
    External("M8 16l8.5-8.5M9 7.5h7.5V15"),
    Card(rect(3f, 6f, 18f, 12.5f, 2.5f), "M3 10h18M6.5 15h4"),
    Wifi("M2.8 9.3a13 13 0 0 1 18.4 0M5.8 12.6a8.7 8.7 0 0 1 12.4 0M8.8 15.9a4.3 4.3 0 0 1 6.4 0M12 19.2v.1"),
    Sidebar(rect(3.5f, 5f, 17f, 14f, 2.5f), "M9.5 5v14"),
    Plus("M12 5v14M5 12h14"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Passkey(
        circle(9f, 8f, 3.5f),
        "M3 19.5c.7-3.4 3.1-5.2 6-5.2 1.1 0 2.2.3 3.1.8",
        circle(17.5f, 12.5f, 2.2f),
        "M17.5 14.7v5.3M17.5 18h1.8",
    ),
    Warn("M12 4.5l8.5 15h-17z", "M12 10v4M12 16.8v.2"),
    Code(circle(12f, 12f, 8f), "M12 7.5V12l3 2"),
    Close("M6.5 6.5l11 11M17.5 6.5l-11 11"),
    Bank("M12 3.8l8 4.4H4z", "M5.5 10.5v7M9.8 10.5v7M14.2 10.5v7M18.5 10.5v7M3.8 20.2h16.4"),
    Chart("M4 19.5h16", "M6.5 15.5l4-4.5 3 3 5-6"),
    IdCard(rect(3f, 5.5f, 18f, 13f, 2.5f), circle(9f, 11f, 2f), "M5.8 16c.6-1.3 1.8-2 3.2-2s2.6.7 3.2 2M14.5 10h4M14.5 13.5h3"),
    Cap("M12 5l9.5 4.5L12 14 2.5 9.5z", "M6.5 11.5v4c1.4 1.4 3.3 2.1 5.5 2.1s4.1-.7 5.5-2.1v-4M21.5 9.5v5"),
    Pen("M14.5 4.5l5 5-8.5 8.5H6v-5z", "M3.5 20.5h17"),
    Badge(rect(4f, 4f, 16f, 16f, 4f), "M8.5 12.2l2.4 2.4 4.6-5"),
    Plus2("M12 6v12M6 12h12"),

    /** The app mark: a page with a padlock on it - notes and secrets in one place. */
    AppMark("M7 4.5h7.5l3.5 3.5v11.5H7z", rect(9.5f, 12f, 6f, 4.5f, 1f), "M10.8 12v-1.2a1.7 1.7 0 0 1 3.4 0V12"),
    ;

    private val cache = HashMap<Float, ImageVector>()

    /** This icon drawn with the given stroke width (the CSS uses 1.8 by default, 2-2.2 in pills and tiles). */
    fun vector(stroke: Float = 1.8f): ImageVector = cache.getOrPut(stroke) {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in parts) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        b.build()
    }
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
    val iw = w - 2 * rx
    val ih = h - 2 * rx
    return "M${x + rx},${y}h${iw}a$rx,$rx 0 0 1 $rx,${rx}v${ih}a$rx,$rx 0 0 1 ${-rx},${rx}" +
        "h${-iw}a$rx,$rx 0 0 1 ${-rx},${-rx}v${-ih}a$rx,$rx 0 0 1 $rx,${-rx}z"
}
