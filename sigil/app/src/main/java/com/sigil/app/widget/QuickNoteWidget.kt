package com.sigil.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.sigil.app.SigilApplication
import kotlinx.coroutines.launch

/** Quick note: a New note button and the title of the last edited note that is not sealed. */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Show something at once, then fill in the title from the database.
        appWidgetIds.forEach { manager.updateAppWidget(it, SigilWidgets.quickNoteViews(context, null)) }
        val app = context.applicationContext as? SigilApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                SigilWidgets.refreshQuickNote(app)
            } finally {
                pending.finish()
            }
        }
    }
}
