package com.sigil.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.sigil.app.MainActivity
import com.sigil.app.R
import com.sigil.app.SigilApplication
import com.sigil.app.logic.NowModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Builds and refreshes the two home-screen widgets. Both work with no data at all. */
object SigilWidgets {
    private const val SCRUBBED_MS = 2_500L

    /** What the Quick note widget says under its heading: the last edited note that is not sealed. */
    fun quickNoteLine(lastTitle: String?): String? = lastTitle?.takeIf { it.isNotBlank() }

    fun quickNoteViews(context: Context, lastTitle: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
            setTextViewText(R.id.qn_title, quickNoteLine(lastTitle) ?: context.getString(R.string.widget_no_notes))
            setOnClickPendingIntent(R.id.qn_new, linkIntent(context, "sigil://note/quick", 1))
            setOnClickPendingIntent(R.id.qn_root, openIntent(context))
        }

    fun scrubViews(context: Context, scrubbed: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_scrub).apply {
            setTextViewText(R.id.sc_label, context.getString(if (scrubbed) R.string.widget_scrubbed else R.string.widget_scrub))
            setOnClickPendingIntent(R.id.sc_root, PendingIntent.getActivity(
                context, 3, Intent(context, ScrubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        }

    private fun linkIntent(context: Context, uri: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    /** Re-reads the last note and redraws every Quick note widget. Safe to call when none are placed. */
    suspend fun refreshQuickNote(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = ids(context, QuickNoteWidget::class.java)
        if (ids.isEmpty()) return
        val app = context.applicationContext as? SigilApplication
        val title = runCatching { app?.repository?.allItems()?.let(NowModel::lastNote)?.title }.getOrNull()
        ids.forEach { manager.updateAppWidget(it, quickNoteViews(context, title)) }
    }

    fun refreshScrub(context: Context, scrubbed: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        ids(context, ScrubWidget::class.java).forEach { manager.updateAppWidget(it, scrubViews(context, scrubbed)) }
    }

    /** Shows "Scrubbed" now and puts the widget back after a moment. */
    fun flashScrubbed(app: SigilApplication) {
        refreshScrub(app, true)
        app.appScope.launch {
            delay(SCRUBBED_MS)
            refreshScrub(app, false)
        }
    }
}
