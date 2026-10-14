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

/** An App as the Launcher shows it. The Settings Tile is one too, with no pictures. */
class Tile(
    val key: String,
    val label: String,
    val banner: ImageBitmap? = null,
    val icon: ImageBitmap? = null,
)

/** An installed TV App: its Tile and how to open it. */
class App(val tile: Tile, val launch: Intent)

/** What the Launcher shows: the installed TV Apps by package name, and the Settings Tile. */
class Installed(val apps: Map<String, App>, val settings: Tile)

/**
 * The installed TV Apps and the Settings Tile. The App that opens the system settings gets no Tile
 * of its own: its banner goes on the Settings Tile instead, so there is one way into Settings.
 * Slow (it decodes every banner): call it off the main thread.
 */
fun loadInstalled(context: Context): Installed {
    val pm = context.packageManager
    @Suppress("DEPRECATION") // the flags overload needs API 33
    val settingsApp = pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    @Suppress("DEPRECATION")
    val found = pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName } // one Tile per App
    val apps = found.filter { it.activityInfo.packageName != settingsApp }.associate { info ->
        val activity = info.activityInfo
        val launch = Intent(query)
            .setComponent(ComponentName(activity.packageName, activity.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val banner = activity.banner(pm)
        val icon = if (banner == null) activity.loadIcon(pm)?.toBitmap(144, 144)?.asImageBitmap() else null
        val tile = Tile(activity.packageName, info.loadLabel(pm).toString(), banner, icon)
        activity.packageName to App(tile, launch)
    }
    val settingsBanner = found.firstOrNull { it.activityInfo.packageName == settingsApp }?.activityInfo?.banner(pm)
    return Installed(apps, Tile(TileOrder.SETTINGS, context.getString(R.string.settings), settingsBanner))
}

// Banners are 16:9 (320×180 dp). Decoded at 480×270 px: sharp on a 1080p TV, small in memory.
private fun ActivityInfo.banner(pm: PackageManager): ImageBitmap? =
    loadBanner(pm)?.toBitmap(480, 270)?.asImageBitmap()

/** Where the Tile order is kept between restarts: one package name per line. */
class OrderStore(context: Context) {
    private val prefs = context.getSharedPreferences("tiles", Context.MODE_PRIVATE)

    fun load(): List<String> = prefs.getString(KEY, null)?.lines()?.filter { it.isNotEmpty() } ?: emptyList()

    fun save(keys: List<String>) {
        prefs.edit().putString(KEY, keys.filter { it != TileOrder.SETTINGS }.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "order"
    }
}
