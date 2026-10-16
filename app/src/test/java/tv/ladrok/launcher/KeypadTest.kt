// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeypadTest {
    @Test fun `nine Tiles to a page`() {
        assertEquals(1, Keypad.pages(0))
        assertEquals(1, Keypad.pages(9))
        assertEquals(2, Keypad.pages(10))
        assertEquals(0, Keypad.pageOf(8))
        assertEquals(1, Keypad.pageOf(9))
    }

    @Test fun `a digit opens the Tile in that cell of the page`() {
        assertEquals(0, Keypad.indexOf(page = 0, digit = 1, tiles = 12))
        assertEquals(8, Keypad.indexOf(page = 0, digit = 9, tiles = 12))
        assertEquals(10, Keypad.indexOf(page = 1, digit = 2, tiles = 12))
    }

    @Test fun `a digit on an empty cell opens nothing`() {
        assertNull(Keypad.indexOf(page = 1, digit = 5, tiles = 12))
        assertNull(Keypad.indexOf(page = 0, digit = 0, tiles = 12))
    }

    @Test fun `digits start again on every page`() {
        assertEquals(1, Keypad.digitOf(0))
        assertEquals(9, Keypad.digitOf(8))
        assertEquals(1, Keypad.digitOf(9))
    }
}

class ShelvesTest {
    private fun program(key: String, app: String = "nuvio", uri: String? = "intent:$key") =
        Program(key, app, key, poster = null, intentUri = uri)

    @Test fun `Watch Next comes first`() {
        val rows = Shelves.arrange(Shelf("w", "Watch Next", listOf(program("a"))), listOf(Shelf("c", "Trending", listOf(program("b")))))
        assertEquals(listOf("w", "c"), rows.map { it.key })
    }

    @Test fun `a Program in Watch Next isn't repeated in a Channel`() {
        val rows = Shelves.arrange(
            Shelf("w", "Watch Next", listOf(program("a"))),
            listOf(Shelf("c", "Continue", listOf(program("a2", uri = "intent:a"), program("b")))),
        )
        assertEquals(listOf("b"), rows[1].programs.map { it.key })
    }

    @Test fun `empty rows are dropped`() {
        val rows = Shelves.arrange(Shelf("w", "Watch Next", emptyList()), listOf(Shelf("c", "Trending", emptyList())))
        assertEquals(emptyList<String>(), rows.map { it.key })
    }

    @Test fun `a row shows at most twelve Programs`() {
        val rows = Shelves.arrange(null, listOf(Shelf("c", "Trending", (1..20).map { program("p$it") })))
        assertEquals(12, rows.single().programs.size)
    }

    @Test fun `the most recent Watch Next Program is the one to continue`() {
        val (spot, rows) = Shelves.spotlight(
            listOf(Shelf(Shelves.WATCH_NEXT, "Watch Next", listOf(program("a"), program("b"))), Shelf("c", "Trending", listOf(program("c")))),
        )
        assertEquals("a", spot?.key)
        assertEquals(listOf(listOf("b"), listOf("c")), rows.map { row -> row.programs.map { it.key } })
    }

    @Test fun `Watch Next with one Program leaves no row behind`() {
        val (spot, rows) = Shelves.spotlight(listOf(Shelf(Shelves.WATCH_NEXT, "Watch Next", listOf(program("a")))))
        assertEquals("a", spot?.key)
        assertEquals(emptyList<String>(), rows.map { it.key })
    }

    @Test fun `without Watch Next nothing is singled out`() {
        val channels = listOf(Shelf("c", "Trending", listOf(program("c"))))
        val (spot, rows) = Shelves.spotlight(channels)
        assertNull(spot)
        assertEquals(channels, rows)
    }

    @Test fun `time left rounds up to whole minutes`() {
        assertEquals(1, Shelves.minutesLeft(1_000))
        assertEquals(24, Shelves.minutesLeft(23 * 60_000L + 1))
        assertEquals(24, Shelves.minutesLeft(24 * 60_000L))
    }
}
