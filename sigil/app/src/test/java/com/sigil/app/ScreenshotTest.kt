package com.sigil.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.ui.Section
import com.sigil.app.ui.SigilViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app (sample data, real ViewModel, real Room database) into PNGs under
 * app/build/outputs/roborazzi, one per main surface, to compare against the prototype.
 * Run with: ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestSigilApplication::class, sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm: SigilViewModel get() = ViewModelProvider(rule.activity)[SigilViewModel::class.java]

    private fun ready() {
        rule.waitUntil(10_000) { vm.state.value.ready && vm.state.value.items.size >= 17 && vm.state.value.checkup.total > 0 }
        rule.waitForIdle()
    }

    private fun act(block: SigilViewModel.() -> Unit) {
        rule.runOnUiThread { vm.block() }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun id(predicate: (com.sigil.app.data.model.Item) -> Boolean) = vm.state.value.items.first(predicate).id

    @Test fun phoneScreens() {
        ready()
        shot("01_everything")
        act { showSidebar() }
        shot("02_library")
        act { go(Section.PASSWORDS) }
        shot("03_passwords")
        act { open(id { it is NoteItem && it.title == "Lake house weekend" }) }
        shot("04_note")
        act { select(id { it is WifiItem }) }
        act { reveal("${id { it is WifiItem }}:pw", (vm.state.value.selected as WifiItem).passwordSecretId) }
        rule.waitUntil(5_000) { vm.state.value.revealed.isNotEmpty() }
        shot("05_wifi_revealed")
        act { select(id { it is CardItem }) }
        shot("06_card")
        act { select(id { it is LoginItem && it.title == "Google" }) }
        shot("07_login_issue")
        act { go(Section.CHECKUP) }
        shot("08_checkup")
        act { openFind("Proj") }
        shot("09_find")
        act { closeOverlay(); go(Section.PASSWORDS); openGenerator() }
        shot("10_generator")
        act { closeOverlay(); lock() }
        shot("11_locked")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun phoneLight() {
        ready()
        shot("12_everything_light")
        act { select(id { it is NoteItem && it.title == "Project Aurora launch notes" }) }
        shot("13_note_light")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-night-mdpi")
    fun tablet() {
        ready()
        act { select(id { it is NoteItem && it.title == "Lake house weekend" }) }
        shot("14_tablet")
    }
}
