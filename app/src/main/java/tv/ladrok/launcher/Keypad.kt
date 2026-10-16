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
    /** When the viewer last watched it (Watch Next only), or null. */
    val at: Long? = null,
    /** Nothing known but the App: shown with the App's banner, opens the App. */
    val onlyApp: Boolean = false,
)

/** One row on the left: Watch Next, or a Channel the viewer switched on. [app]: the Channel's App. */
class Shelf(val key: String, val title: String, val programs: List<Program>, val app: String? = null)

object Shelves {
    const val MAX_PROGRAMS = 12
    const val WATCH_NEXT = "watch-next"

    /** The most recent Program in Watch Next, if any. */
    fun latest(rows: List<Shelf>): Program? = rows.firstOrNull()?.takeIf { it.key == WATCH_NEXT }?.programs?.firstOrNull()

    /**
     * The Program to continue, shown large at the top ([pick], by default the most recent in Watch
     * Next), and the rows below it without that Program.
     */
    fun spotlight(rows: List<Shelf>, pick: Program? = latest(rows)): Pair<Program?, List<Shelf>> {
        pick ?: return null to rows
        val rest = rows.map { row ->
            if (row.key == WATCH_NEXT) Shelf(row.key, row.title, row.programs.filter { it.key != pick.key }, row.app) else row
        }
        return pick to rest.filter { it.programs.isNotEmpty() }
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

/** An App the viewer opened from the Launcher and stayed in for a while, from [started]. */
class Visit(val app: String, val started: Long)

/** What an App's player last said it was playing, at [at]. [poster]: a local file, or null. */
class Playing(val app: String, val at: Long, val title: String, val poster: String?, val position: Long, val duration: Long)

/**
 * What the large card on the home screen continues, for any App. Watch Next has the most to show,
 * but only some Apps write it; so the App the viewer was in last wins, shown as richly as it allows:
 * its Watch Next, else what its player said it was playing, else just the App.
 */
object Continue {
    /** Shorter visits (a look at Settings, a wrong App) don't replace what the viewer was watching. */
    const val MIN_VISIT = 60_000L

    fun pick(watchNext: List<Program>, visit: Visit?, playing: Playing?): Program? {
        val next = watchNext.firstOrNull()
        if (visit == null) return next
        // The App's own Watch Next, or a Watch Next written since that visit began.
        if (next != null && (next.app == visit.app || (next.at ?: 0) >= visit.started)) return next
        if (playing != null && playing.app == visit.app && playing.at >= visit.started) {
            val left = if (playing.duration > playing.position && playing.position > 0) playing.duration - playing.position else null
            return Program(
                key = "playing:${playing.app}",
                app = playing.app,
                title = playing.title,
                poster = playing.poster,
                intentUri = null,
                progress = if (left != null) playing.position.toFloat() / playing.duration else null,
                left = left,
            )
        }
        return Program(key = "app:${visit.app}", app = visit.app, title = "", poster = null, intentUri = null, onlyApp = true)
    }
}
