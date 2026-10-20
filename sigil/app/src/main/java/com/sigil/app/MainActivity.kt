package com.sigil.app

import android.content.Intent
import android.os.Bundle
import java.time.LocalDate
import com.sigil.app.ipc.DeepLink
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sigil.app.designsystem.LocalLumeHaptics
import com.sigil.app.ui.Haptics
import kotlinx.coroutines.launch
import com.sigil.app.ui.LocalPresence
import com.sigil.app.ui.Presence
import com.sigil.app.ui.SigilApp
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.theme.SigilTheme

/** A FragmentActivity (not just ComponentActivity) because the system biometric prompt needs one. */
class MainActivity : FragmentActivity() {
    private val vm: SigilViewModel by viewModels()

    private var presence: Presence? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        presence?.let { p -> window.decorView.post { handleDeepLink(intent, p) } }
    }

    /** Every link needs the biometric check first, so a web page or app can't write notes or clear the clipboard unseen. */
    private fun handleDeepLink(intent: Intent, presence: Presence) {
        val uri = intent.data ?: return
        val link = DeepLink.parse(uri.scheme, uri.host, uri.path) ?: return
        intent.data = null
        val reason = when (link) {
            DeepLink.QUICK_NOTE -> "Open a quick note"
            DeepLink.DIARY -> "Open tonight's diary"
            DeepLink.SCRUB_CLIPBOARD -> "Clear the clipboard"
        }
        presence.authenticate(reason) {
            if (vm.state.value.locked) vm.unlocked()
            when (link) {
                DeepLink.QUICK_NOTE -> vm.quickNote()
                DeepLink.DIARY -> vm.diaryNote(LocalDate.now())
                DeepLink.SCRUB_CLIPBOARD -> vm.scrubClipboard()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val presence = Presence(this, vm)
        val haptics = Haptics(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.haptics.collect(haptics::play) }
        }
        lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.onBackgrounded() },
        )
        if (savedInstanceState == null) window.decorView.post { handleDeepLink(intent, presence) }
        this.presence = presence
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            // Sealed values are kept out of screenshots, screen recordings and the app switcher
            // whenever one is on screen; the rest of Sigil can be captured like any app.
            val secure = state.showsSecrets
            SideEffect {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            SigilTheme {
                CompositionLocalProvider(LocalPresence provides presence, LocalLumeHaptics provides haptics) {
                    SigilApp(state, vm)
                }
            }
        }
    }
}
