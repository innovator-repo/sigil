package com.sigil.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * "Copies are local-only, marked concealed, and cleared after 60 seconds." Secrets are
 * flagged sensitive so Android 13+ hides them from the clipboard preview, and the clip is
 * cleared after a minute unless something else has been copied since.
 */
class SensitiveClipboard(context: Context, private val scope: CoroutineScope) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    fun copy(text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(if (sensitive) SENSITIVE_LABEL else "Sigil", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
                putBoolean(key, true)
            }
        }
        manager.setPrimaryClip(clip)
        clearJob?.cancel()
        if (sensitive) {
            clearJob = scope.launch {
                delay(CLEAR_AFTER_MS)
                withContext(Dispatchers.Main) { clearIfStillOurs() }
            }
        }
    }

    /** Wipes whatever is on the clipboard now, whoever put it there. */
    fun scrub() {
        clearJob?.cancel()
        clear()
    }

    private fun clearIfStillOurs() {
        // While Sigil isn't in front, Android hides the clip description; clear anyway, as promised.
        val label = runCatching { manager.primaryClipDescription?.label }.getOrNull()
        if (label != null && label != SENSITIVE_LABEL) return
        clear()
    }

    private fun clear() {
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { manager.clearPrimaryClip() }
        } else {
            runCatching { manager.setPrimaryClip(ClipData.newPlainText("", "")) }
        }
    }

    companion object {
        private const val SENSITIVE_LABEL = "Sigil secret"
        const val CLEAR_AFTER_MS = 60_000L
    }
}
