package com.sigil.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sigil.app.ui.SigilViewModel
import com.sigil.app.ui.UiState
import com.sigil.app.ui.components.BackButton
import com.sigil.app.ui.components.SButton
import com.sigil.app.ui.theme.Sigil
import com.sigil.app.ui.theme.SigilType

/** The Clipboard chip: wipe whatever is on the clipboard now. Copies of secrets already clear themselves after a minute. */
@Composable
fun ClipboardPane(state: UiState, vm: SigilViewModel, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    Column(modifier.background(c.paper).statusBarsPadding()) {
        TopBar { vm.showHome() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Clipboard", style = SigilType.largeTitle, color = c.label)
            Spacer(Modifier.height(8.dp))
            Text(
                "Anything Sigil copies is marked private and clears itself after a minute. " +
                    "Scrub it now to wipe whatever is on the clipboard, whoever put it there.",
                style = SigilType.body, color = c.label2,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.clipArmed()) "An automatic clear is waiting." else "No automatic clear is waiting.",
                style = SigilType.body, color = c.label3,
            )
            Spacer(Modifier.height(20.dp))
            SButton("Scrub clipboard now", { vm.scrubClipboard() }, Modifier.fillMaxWidth().height(52.dp), primary = true)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** The gear: locking Sigil, the autofill service, and the version. */
@Composable
fun SettingsPane(state: UiState, vm: SigilViewModel, modifier: Modifier = Modifier) {
    val c = Sigil.colors
    val context = LocalContext.current
    Column(modifier.background(c.paper).statusBarsPadding()) {
        TopBar { vm.showHome() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Settings", style = SigilType.largeTitle, color = c.label)
            Spacer(Modifier.height(16.dp))
            Text("Lock Sigil now. Sealed things then need your fingerprint or screen lock again.", style = SigilType.body, color = c.label2)
            Spacer(Modifier.height(10.dp))
            SButton("Lock Sigil", { vm.lock() }, Modifier.fillMaxWidth().height(52.dp))
            Spacer(Modifier.height(24.dp))
            Text("Fill logins in other apps and websites by choosing Sigil as the autofill service.", style = SigilType.body, color = c.label2)
            Spacer(Modifier.height(10.dp))
            SButton("Autofill settings", {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }, Modifier.fillMaxWidth().height(52.dp))
            Spacer(Modifier.height(24.dp))
            Text("Sigil ${version(context)}", style = SigilType.meta, color = c.label3)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 4.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton("Sigil", onBack)
    }
}

private fun version(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
