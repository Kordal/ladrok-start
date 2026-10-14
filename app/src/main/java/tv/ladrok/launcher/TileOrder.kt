// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import java.text.Collator

/** The rules for which Tiles there are and in what order. Plain Kotlin, so they can be unit tested. */
object TileOrder {
    /** The key of the Settings Tile. App keys are package names, which can't start with "#". */
    const val SETTINGS = "#settings"

    /** Apps that never get a Tile. System update is already in Settings → About and prompts by itself. */
    val HIDDEN = setOf("tv.ladrok.updater")

    /**
     * The keys of the Tiles to show, in order: the [saved] order of the Apps still installed, then
     * Apps not seen before in alphabetical order, then the Settings Tile. [installed] maps each
     * App's package name to its label.
     */
    fun arrange(saved: List<String>, installed: Map<String, String>): List<String> {
        val visible = installed.filterKeys { it !in HIDDEN }
        val kept = saved.distinct().filter { it in visible }
        val collator = Collator.getInstance()
        val added = (visible.keys - kept.toSet())
            .sortedWith(compareBy(collator) { key: String -> visible.getValue(key) }.thenBy { it })
        return kept + added + SETTINGS
    }

    /** [keys] with the Tile at [from] moved to [to]. The Settings Tile can't move and stays last. */
    fun move(keys: List<String>, from: Int, to: Int): List<String> {
        val lastMovable = keys.lastIndex - 1
        if (from !in 0..lastMovable) return keys
        val target = to.coerceIn(0, lastMovable)
        if (target == from) return keys
        return keys.toMutableList().apply { add(target, removeAt(from)) }
    }
}
