// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An App as the Keypad shows it. The Settings Tile is one too. */
class Tile(
    val key: String,
    val label: String,
    val icon: ImageBitmap? = null,
)

/** An installed TV App: its Tile and how to open it. */
class App(val tile: Tile, val launch: Intent)

/** What the Launcher shows: the installed TV Apps by package name, and the Settings Tile. */
class Installed(val apps: Map<String, App>, val settings: Tile)

/**
 * The installed TV Apps and the Settings Tile. The App that opens the system settings gets no Tile
 * of its own: its icon goes on the Settings Tile instead, so there is one way into Settings.
 * Slow (it decodes every icon): call it off the main thread.
 */
fun loadInstalled(context: Context): Installed {
    val pm = context.packageManager
    @Suppress("DEPRECATION") // the flags overload needs API 33
    val settingsApp = pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    @Suppress("DEPRECATION")
    val found = pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName } // one Tile per App
    val apps = found.filter { it.activityInfo.packageName != settingsApp?.packageName }.associate { info ->
        val activity = info.activityInfo
        val launch = Intent(query)
            .setComponent(ComponentName(activity.packageName, activity.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        activity.packageName to App(Tile(activity.packageName, info.loadLabel(pm).toString(), activity.icon(pm)), launch)
    }
    val settingsIcon = (found.firstOrNull { it.activityInfo.packageName == settingsApp?.packageName }?.activityInfo ?: settingsApp)?.icon(pm)
    return Installed(apps, Tile(TileOrder.SETTINGS, context.getString(R.string.settings), settingsIcon))
}

// Decoded at 192 px: sharp at the Keypad's icon size on a 1080p TV, small in memory.
private fun ActivityInfo.icon(pm: PackageManager): ImageBitmap? =
    loadIcon(pm)?.toBitmap(192, 192)?.asImageBitmap()

/** What the Launcher remembers between restarts: the Tile order and the switched-on Channels. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tiles", Context.MODE_PRIVATE)

    /** The Tile order: one package name per line. */
    fun order(): List<String> = prefs.getString(ORDER, null)?.lines()?.filter { it.isNotEmpty() } ?: emptyList()

    fun saveOrder(keys: List<String>) {
        prefs.edit().putString(ORDER, keys.filter { it != TileOrder.SETTINGS }.joinToString("\n")).apply()
    }

    /** The keys of the Channels the viewer switched on. */
    fun channels(): Set<String> = prefs.getStringSet(CHANNELS, null).orEmpty()

    fun toggleChannel(key: String) {
        val now = channels()
        prefs.edit().putStringSet(CHANNELS, if (key in now) now - key else now + key).apply()
    }

    /** Apps whose Watch Next the viewer turned off (a Tile's menu, or a Program's). */
    fun hiddenApps(): Set<String> = prefs.getStringSet(HIDDEN_APPS, null).orEmpty()

    fun toggleWatchNext(app: String) {
        val now = hiddenApps()
        prefs.edit().putStringSet(HIDDEN_APPS, if (app in now) now - app else now + app).apply()
    }

    /** Programs the viewer removed from Watch Next ([Program.same]), with when. */
    fun removed(): Map<String, Long> = prefs.getStringSet(REMOVED, null).orEmpty().mapNotNull { entry ->
        entry.substringBefore('|').toLongOrNull()?.let { entry.substringAfter('|') to it }
    }.toMap()

    fun remove(program: Program) {
        val kept = removed() - program.same + (program.same to System.currentTimeMillis())
        prefs.edit().putStringSet(REMOVED, kept.map { (same, at) -> "$at|$same" }.takeLast(MAX_REMOVED).toSet()).apply()
    }

    /** Whether the Launcher already asked for the TvProvider permission. It asks only once. */
    var askedPermission: Boolean
        get() = prefs.getBoolean(ASKED, false)
        set(value) = prefs.edit().putBoolean(ASKED, value).apply()

    private companion object {
        const val ORDER = "order"
        const val CHANNELS = "channels"
        const val ASKED = "asked_permission"
        const val HIDDEN_APPS = "watch_next_off"
        const val REMOVED = "watch_next_removed"
        const val MAX_REMOVED = 200
    }
}
