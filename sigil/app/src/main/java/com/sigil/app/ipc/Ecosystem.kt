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

    private val KASANE_STATUS = android.net.Uri.parse("content://app.kasane.provider/status")
    private val VOW_STATUS = android.net.Uri.parse("content://com.vow.app.provider/status")

    /**
     * A plain-words recap of today from Kasane and Vow's read-only status providers, or null when
     * neither is installed or reachable. Numbers only; nothing is sent out.
     */
    fun diarySummary(context: Context): String? {
        val lines = ArrayList<String>()
        queryRow(context, KASANE_STATUS)?.let { r ->
            val kept = r["rulesKept"]; val total = r["rulesTotal"]
            if (kept != null && total != null) lines += "Rules kept: $kept of $total"
            r["momentum"]?.let { lines += "Momentum: $it" }
        }
        queryRow(context, VOW_STATUS)?.let { r ->
            r["reclaimedMinutes"]?.let { lines += "Time reclaimed: $it min" }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun queryRow(context: Context, uri: android.net.Uri): Map<String, String>? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            (0 until c.columnCount).associate { c.getColumnName(it) to c.getString(it) }
        }
    }.getOrNull()

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
