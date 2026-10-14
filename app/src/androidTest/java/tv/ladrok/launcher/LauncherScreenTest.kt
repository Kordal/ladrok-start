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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tv.ladrok.launcher.TileOrder.SETTINGS

/** Drives the Launcher with the D-pad, as from the couch. */
@OptIn(ExperimentalTestApi::class)
class LauncherScreenTest {
    @get:Rule val rule = createComposeRule()

    private val all = listOf(Tile("a", "Alpha"), Tile("b", "Beta"), Tile("c", "Gamma"), Tile(SETTINGS, "Settings"))
    private var tiles by mutableStateOf(all)
    private var home by mutableStateOf(0)

    private fun show() = rule.setContent {
        LauncherScreen(
            tiles = tiles,
            home = home,
            onOpen = {},
            onAppInfo = {},
            onMove = { from, to ->
                val keys = TileOrder.move(tiles.map { it.key }, from, to)
                tiles = keys.map { key -> all.first { it.key == key } }
            },
        )
    }

    private fun press(label: String, key: Key) =
        rule.onNodeWithContentDescription(label).performKeyInput { pressKey(key) }

    @Test fun firstTileHasFocusAndArrowsMoveIt() {
        show()
        rule.onNodeWithContentDescription("Alpha").assertIsFocused()
        press("Alpha", Key.DirectionRight)
        rule.onNodeWithContentDescription("Beta").assertIsFocused()
        rule.onNodeWithTag("caption").assertTextEquals("Beta") // the focused App's name under the grid
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
}
