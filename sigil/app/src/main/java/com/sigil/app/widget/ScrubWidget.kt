package com.sigil.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Scrub clipboard: one tap wipes the clipboard (see [ScrubActivity]) and says "Scrubbed" for a moment. */
class ScrubWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, SigilWidgets.scrubViews(context, false)) }
    }
}
