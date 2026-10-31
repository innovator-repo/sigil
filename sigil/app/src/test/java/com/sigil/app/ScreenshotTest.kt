package com.sigil.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.WifiItem
import com.sigil.app.ui.Section
import com.sigil.app.ui.Overlay
import com.sigil.app.data.model.Category
import com.sigil.app.data.model.DraftField
import com.sigil.app.data.model.ItemDraft
import com.sigil.app.data.model.Space
import androidx.compose.ui.test.hasText
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

    @org.junit.Before fun pinClock() {
        com.sigil.app.designsystem.CircadianPhase.override = com.sigil.app.designsystem.CircadianPhase.DAY
    }

    @org.junit.After fun releaseClock() {
        com.sigil.app.designsystem.CircadianPhase.override = null
    }

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

    private fun com.sigil.app.data.model.Item.title() = (this as? LoginItem)?.title

    private fun id(predicate: (com.sigil.app.data.model.Item) -> Boolean) = vm.state.value.items.first(predicate).id

    @Test fun phoneScreens() {
        ready()
        shot("01_everything")
        act { showHome() }
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
        rule.onNodeWithText("Mine").performClick()
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("Summer2019!")
        rule.waitForIdle()
        shot("18_own_password")
        act { closeOverlay(); startSelection(id { it.title() == "Google" }); toggleSelection(id { it.title() == "Airbnb" }) }
        shot("15_multiselect")
        act { endSelection(); go(Section.DELETED); select(vm.state.value.listItems().first().id) }
        shot("16_deleted_item")
        act { confirmDeleteForever(setOf(vm.state.value.selectedId!!)) }
        shot("17_confirm_delete")
        act { closeOverlay(); go(Section.PASSWORDS); openNewItem() }
        shot("19_new_item")
        act { openEditor(Overlay.Editor(category = Category.BANK)) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("New Bank Account")).fetchSemanticsNodes().isNotEmpty() }
        shot("20_bank_editor")
        act {
            saveDraft(
                Overlay.Editor(category = Category.BANK),
                ItemDraft.Login(
                    Space.PERSONAL, "HDFC Bank", Category.BANK, "50123456", "Hdfc@Net2024", "netbanking.hdfcbank.com",
                    listOf(
                        DraftField("a", "Account number", "50100123456789", true),
                        DraftField("b", "IFSC", "HDFC0001234", false),
                        DraftField("c", "MPIN", "4821", true),
                        DraftField("d", "App MPIN", "9035", true),
                    ),
                    "Branch: Sector 17, Chandigarh",
                ),
                null,
            )
        }
        rule.waitUntil(5_000) { vm.state.value.selected?.let { it is LoginItem && it.title == "HDFC Bank" } == true }
        rule.waitForIdle()
        shot("21_bank_detail")
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
