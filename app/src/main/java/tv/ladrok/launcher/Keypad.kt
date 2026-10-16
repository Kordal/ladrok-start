// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

/** The Keypad's paging rules: Tiles in pages of nine, three by three, digit 1 top left. Plain Kotlin. */
object Keypad {
    const val COLUMNS = 3
    const val PER_PAGE = COLUMNS * COLUMNS

    fun pages(tiles: Int): Int = maxOf(1, (tiles + PER_PAGE - 1) / PER_PAGE)

    fun pageOf(index: Int): Int = index / PER_PAGE

    /** The Tile index that [digit] (1–9) opens on [page], or null when that cell is empty. */
    fun indexOf(page: Int, digit: Int, tiles: Int): Int? {
        if (digit !in 1..PER_PAGE) return null
        val index = page * PER_PAGE + digit - 1
        return index.takeIf { it < tiles }
    }

    /** The digit shown on the Tile at [index]. */
    fun digitOf(index: Int): Int = index % PER_PAGE + 1
}

/** A Program as the Launcher shows it: plain data, so the row rules can be unit tested. */
class Program(
    val key: String,
    val app: String,
    val title: String,
    val poster: String?,
    val intentUri: String?,
    /** How far the viewer got, 0..1, or null when the App doesn't say. */
    val progress: Float? = null,
    /** Milliseconds still to watch, or null when the App doesn't say. */
    val left: Long? = null,
)

/** One row on the left: Watch Next, or a Channel the viewer switched on. [app]: the Channel's App. */
class Shelf(val key: String, val title: String, val programs: List<Program>, val app: String? = null)

object Shelves {
    const val MAX_PROGRAMS = 12
    const val WATCH_NEXT = "watch-next"

    /**
     * The Program to continue, shown large at the top (the most recent in Watch Next, if any), and
     * the rows below it without that Program.
     */
    fun spotlight(rows: List<Shelf>): Pair<Program?, List<Shelf>> {
        val first = rows.firstOrNull()?.takeIf { it.key == WATCH_NEXT } ?: return null to rows
        val rest = Shelf(first.key, first.title, first.programs.drop(1), first.app)
        return first.programs.first() to (listOf(rest) + rows.drop(1)).filter { it.programs.isNotEmpty() }
    }

    /** Whole minutes still to watch, rounded up so the last seconds still read "1 min". */
    fun minutesLeft(left: Long): Long = (left + 59_999) / 60_000

    /**
     * The rows to show: Watch Next first, then the switched-on Channels. A Program already in
     * Watch Next isn't repeated in a Channel, and a row with nothing left in it is dropped.
     */
    fun arrange(watchNext: Shelf?, channels: List<Shelf>): List<Shelf> {
        val seen = watchNext?.programs.orEmpty().map { it.app to (it.intentUri ?: it.title) }.toSet()
        val rows = listOfNotNull(watchNext) + channels.map { channel ->
            Shelf(channel.key, channel.title, channel.programs.filter { (it.app to (it.intentUri ?: it.title)) !in seen }, channel.app)
        }
        return rows
            .map { Shelf(it.key, it.title, it.programs.take(MAX_PROGRAMS), it.app) }
            .filter { it.programs.isNotEmpty() }
    }
}
