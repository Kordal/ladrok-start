// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tv.ladrok.launcher.TileOrder.SETTINGS

/** Drives the Launcher with the remote, as from the couch. */
@OptIn(ExperimentalTestApi::class)
class LauncherScreenTest {
    @get:Rule val rule = createComposeRule()

    private val few = listOf(Tile("a", "Alpha"), Tile("b", "Beta"), Tile("c", "Gamma"), Tile(SETTINGS, "Settings"))
    private var all = few
    private var tiles by mutableStateOf(few)
    private var shelves by mutableStateOf(emptyList<Shelf>())
    private var home by mutableStateOf(0)
    private var hasStore = false
    /** What [Continue] picked in the app; null for the default, the most recent in Watch Next. */
    private var continueWith: Program? = null
    private val opened = mutableListOf<String>()

    private fun show() = rule.setContent {
        LauncherScreen(
            shelves = shelves,
            tiles = tiles,
            appName = { "Nuvio" },
            home = home,
            onOpen = { opened += it.key },
            onOpenProgram = { opened += it.key },
            onAppInfo = {},
            onShowInStore = if (hasStore) ({ opened += "store:${it.key}" }) else null,
            onMove = { from, to ->
                val keys = TileOrder.move(tiles.map { it.key }, from, to)
                tiles = keys.map { key -> all.first { it.key == key } }
            },
            channelsOf = { emptyList() },
            onToggleChannel = {},
            continueWith = continueWith ?: Shelves.latest(shelves),
        )
    }

    private fun press(label: String, key: Key) =
        rule.onNodeWithContentDescription(label).performKeyInput { pressKey(key) }

    @Test fun firstTileHasFocusAndArrowsMoveIt() {
        show()
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
        press("Alpha", Key.DirectionRight)
        rule.onNodeWithContentDescription("Beta").assertIsFocused()
        rule.onNodeWithTag("caption").assertTextEquals("Beta") // the focused App's name under everything
    }

    @Test fun longPressOkThenMoveRearrangesTiles() {
        show()
        press("Alpha", Key.DirectionRight)
        rule.onNodeWithContentDescription("Beta").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        rule.onNodeWithText("Move").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        press("Beta", Key.DirectionRight) // past Gamma
        press("Beta", Key.DirectionRight) // can't pass Settings
        press("Beta", Key.DirectionCenter) // put it down
        assertEquals(listOf("a", "c", "b", SETTINGS), tiles.map { it.key })
        rule.onNodeWithContentDescription("Beta").assertIsFocused()
        press("Beta", Key.DirectionLeft) // Move mode is over: the arrows move the focus again
        rule.onNodeWithContentDescription("Gamma").assertIsFocused()
    }

    @Test fun homeFocusesTheFirstTile() {
        show()
        press("Alpha", Key.DirectionRight)
        press("Beta", Key.DirectionRight)
        home++
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
    }

    @Test fun rightOffTheKeypadTurnsThePage() {
        all = (1..10).map { Tile("app$it", "App $it") } + Tile(SETTINGS, "Settings")
        tiles = all
        show()
        rule.onNodeWithTag("page").assertTextEquals("1 / 2")
        press("App 1", Key.DirectionRight)
        press("App 2", Key.DirectionRight)
        press("App 3", Key.DirectionRight) // off the right edge
        rule.onNodeWithTag("page").assertTextEquals("2 / 2")
        rule.onNodeWithContentDescription("App 10").assertIsFocused()
        press("App 10", Key.DirectionLeft) // and back
        rule.onNodeWithTag("page").assertTextEquals("1 / 2")
        rule.onNodeWithContentDescription("App 3").assertIsFocused()
    }

    @Test fun aDigitOpensThatTile() {
        show()
        press("Alpha", Key.Three)
        assertEquals(listOf("c"), opened)
    }

    @Test fun watchNextComesFirstAndHasTheFocus() {
        shelves = listOf(Shelf("w", "Watch Next", listOf(Program("w1", "nuvio", "Criminal Minds", null, "intent:x", 0.5f))))
        show()
        rule.onNodeWithContentDescription("Criminal Minds").assertIsFocused()
        rule.onNodeWithTag("caption").assertTextEquals("Criminal Minds · Nuvio")
        press("Criminal Minds", Key.DirectionCenter)
        assertEquals(listOf("w1"), opened)
    }

    @Test fun backHopsBetweenTheRowsAndTheKeypad() {
        shelves = listOf(Shelf("w", "Watch Next", (1..20).map { Program("w$it", "nuvio", "Show $it", null, null, null) }))
        show()
        repeat(5) { press("Show ${it + 1}", Key.DirectionRight) } // into the row
        rule.onNodeWithContentDescription("Show 6").assertIsFocused()
        Espresso.pressBack()
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
        press("Alpha", Key.DirectionRight)
        Espresso.pressBack() // back to the start of the rows
        rule.onNodeWithContentDescription("Show 1").assertIsFocused()
        Espresso.pressBack() // and to the Tile that had focus last
        rule.onNodeWithContentDescription("Beta").assertIsFocused()
    }

    @Test fun theProgramToContinueIsLargeFirstAndShowsTheTimeLeft() {
        shelves = listOf(
            Shelf(
                Shelves.WATCH_NEXT, "Watch Next",
                listOf(
                    Program("w1", "nuvio", "Criminal Minds", null, "intent:x", 0.5f, left = 24 * 60_000L),
                    Program("w2", "nuvio", "Severance", null, "intent:y"),
                ),
            ),
        )
        show()
        rule.onNodeWithContentDescription("Criminal Minds").assertIsFocused()
        rule.onNodeWithTag("caption").assertTextEquals("Criminal Minds · Nuvio")
        rule.onNodeWithTag("left").assertTextEquals("24 min left")
        press("Criminal Minds", Key.DirectionRight) // to the Keypad's first Tile
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
        Espresso.pressBack() // and back to the Program to continue
        rule.onNodeWithContentDescription("Criminal Minds").assertIsFocused()
        press("Criminal Minds", Key.DirectionDown) // the rest of Watch Next, below it
        rule.onNodeWithContentDescription("Severance").assertIsFocused()
        press("Severance", Key.DirectionCenter)
        assertEquals(listOf("w2"), opened)
    }

    @Test fun nothingToContinueShowsTheTimeAndFocusesTheKeypad() {
        show()
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
        rule.onNodeWithTag("left").assertDoesNotExist()
    }

    @Test fun aTilesMenuOffersItsPageInTheStore() {
        hasStore = true
        show()
        rule.onNodeWithContentDescription("Alpha").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        rule.onNodeWithText("Move").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Updates").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("store:a"), opened)
    }

    @Test fun anAppThatSaysNothingStillContinuesOnTheLargeCard() {
        continueWith = Program("app:stremio", "stremio", "", null, null, onlyApp = true)
        show()
        rule.onNodeWithContentDescription("stremio").assertIsFocused()
        rule.onNodeWithTag("caption").assertTextEquals("Nuvio")
        rule.onNodeWithText("Pick up where you left off").assertExists()
        press("stremio", Key.DirectionCenter)
        assertEquals(listOf("app:stremio"), opened)
    }
}
