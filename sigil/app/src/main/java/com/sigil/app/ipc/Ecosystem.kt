package com.sigil.app.ipc

import android.content.Context
import android.content.Intent

/**
 * Sigil's small part of the Lume ecosystem: tell Kasane that "diary" or "clipboard" is done.
 * Only an item id and a boolean leave the app, never a note, a title or a secret, and the
 * broadcast is explicit and signature-guarded, so only a sibling app signed with the same
 * key can receive it. If Kasane isn't installed it quietly does nothing.
 */
object Ecosystem {
    const val PERMISSION = "app.lume.permission.ECOSYSTEM_SYNC"
    const val ACTION_LOG_CHECK = "app.kasane.action.LOG_CHECK"
    const val KASANE_PACKAGE = "app.kasane"
    const val ITEM_DIARY = "diary"
    const val ITEM_CLIPBOARD = "clipboard"

    /**
     * A plain-words recap of today from Kasane and Vow's read-only status providers, or null when
     * neither is installed or reachable. Numbers only; nothing is sent out.
     */
    fun diarySummary(context: Context): String? =
        DiaryRecap.lines(Companion.row(context, KASANE_PACKAGE), Companion.row(context, VOW_PACKAGE))
            .takeIf { it.isNotEmpty() }?.joinToString("\n")

    private const val VOW_PACKAGE = "com.vow.app"

    fun logCheck(context: Context, item: String, done: Boolean) {
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_LOG_CHECK)
                    .setPackage(KASANE_PACKAGE)
                    .putExtra("item", item)
                    .putExtra("done", done),
                PERMISSION,
            )
        }
    }
}

/**
 * A cross-app feature is composed only if the companion is installed AND its status provider
 * answers. Any exception or null result means "absent", so callers show nothing at all.
 */
object Companion {
    fun statusUri(packageName: String): android.net.Uri = android.net.Uri.parse("content://$packageName.provider/status")

    /** The companion's status row as column -> text, or null when it is absent. */
    fun row(context: Context, packageName: String): Map<String, String>? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        context.contentResolver.query(statusUri(packageName), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            (0 until c.columnCount).associate { c.getColumnName(it) to c.getString(it) }
        }
    }.getOrNull()

    fun available(context: Context, packageName: String): Boolean = row(context, packageName) != null
}

/** What a `sigil://` link asks for. */
enum class DeepLink {
    QUICK_NOTE, DIARY, SCRUB_CLIPBOARD;

    companion object {
        /** `sigil://note/quick`, `sigil://note/diary`, `sigil://action/scrub-clipboard`; anything else is null. */
        fun parse(scheme: String?, host: String?, path: String?): DeepLink? {
            if (scheme != "sigil") return null
            return when (host to path?.trim('/')) {
                "note" to "quick" -> QUICK_NOTE
                "note" to "diary" -> DIARY
                "action" to "scrub-clipboard" -> SCRUB_CLIPBOARD
                else -> null
            }
        }
    }
}
