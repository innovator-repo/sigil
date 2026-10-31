package com.sigil.app.widget

import android.app.Activity
import android.os.Bundle
import com.sigil.app.SigilApplication
import com.sigil.app.ipc.Ecosystem

/**
 * Invisible stop for the Scrub widget. Android only lets the app in front touch the clipboard,
 * so the tap opens this transparent screen, which scrubs the moment it has focus and closes.
 * It shows nothing and reads nothing; it only clears.
 */
class ScrubActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val app = application as SigilApplication
        app.clipboard.scrub()
        Ecosystem.logCheck(app, Ecosystem.ITEM_CLIPBOARD, true)
        SigilWidgets.flashScrubbed(app)
        finish()
    }
}
