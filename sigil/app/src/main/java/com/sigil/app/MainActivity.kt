package com.sigil.app

import android.os.Bundle
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
import com.sigil.app.ui.LocalPresence
import com.sigil.app.ui.Presence
import com.sigil.app.ui.SigilApp
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.theme.SigilTheme

/** A FragmentActivity (not just ComponentActivity) because the system biometric prompt needs one. */
class MainActivity : FragmentActivity() {
    private val vm: SigilViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val presence = Presence(this, vm)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.onBackgrounded() },
        )
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
                CompositionLocalProvider(LocalPresence provides presence) {
                    SigilApp(state, vm)
                }
            }
        }
    }
}
