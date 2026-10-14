// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.ladrok.launcher.TileOrder.SETTINGS

class TileOrderTest {
    private val installed = mapOf(
        "com.netflix" to "Netflix",
        "com.youtube" to "YouTube",
        "org.kodi" to "Kodi",
        "tv.ladrok.updater" to "System update",
    )

    @Test fun `first boot is alphabetical by label`() {
        assertEquals(listOf("org.kodi", "com.netflix", "com.youtube", SETTINGS), TileOrder.arrange(emptyList(), installed))
    }

    @Test fun `alphabetical ignores case`() {
        val apps = mapOf("a" to "zattoo", "b" to "Apple TV", "c" to "mubi")
        assertEquals(listOf("b", "c", "a", SETTINGS), TileOrder.arrange(emptyList(), apps))
    }

    @Test fun `saved order is kept`() {
        val saved = listOf("com.youtube", "org.kodi", "com.netflix")
        assertEquals(saved + SETTINGS, TileOrder.arrange(saved, installed))
    }

    @Test fun `a new App goes at the end, before Settings`() {
        val saved = listOf("com.youtube", "org.kodi")
        assertEquals(listOf("com.youtube", "org.kodi", "com.netflix", SETTINGS), TileOrder.arrange(saved, installed))
    }

    @Test fun `an uninstalled App drops out`() {
        val saved = listOf("com.youtube", "com.gone", "org.kodi", "com.netflix")
        assertEquals(listOf("com.youtube", "org.kodi", "com.netflix", SETTINGS), TileOrder.arrange(saved, installed))
    }

    @Test fun `System update is always hidden`() {
        val saved = listOf("tv.ladrok.updater", "com.youtube")
        assertEquals(false, "tv.ladrok.updater" in TileOrder.arrange(saved, installed))
    }

    @Test fun `with no Apps there is still the Settings Tile`() {
        assertEquals(listOf(SETTINGS), TileOrder.arrange(listOf("com.gone"), emptyMap()))
    }

    @Test fun `move puts the Tile at the new place`() {
        val keys = listOf("a", "b", "c", "d", SETTINGS)
        assertEquals(listOf("b", "c", "a", "d", SETTINGS), TileOrder.move(keys, 0, 2))
        assertEquals(listOf("d", "a", "b", "c", SETTINGS), TileOrder.move(keys, 3, 0))
    }

    @Test fun `a Tile can't move past Settings`() {
        val keys = listOf("a", "b", "c", SETTINGS)
        assertEquals(listOf("b", "c", "a", SETTINGS), TileOrder.move(keys, 0, 10))
        assertEquals(listOf("c", "a", "b", SETTINGS), TileOrder.move(keys, 2, -5))
    }

    @Test fun `Settings can't be moved`() {
        val keys = listOf("a", "b", SETTINGS)
        assertEquals(keys, TileOrder.move(keys, 2, 0))
    }
}
