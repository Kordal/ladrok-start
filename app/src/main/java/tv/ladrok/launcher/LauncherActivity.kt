// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Ladrok TV home screen. */
class LauncherActivity : ComponentActivity() {
    private lateinit var store: OrderStore
    private var installed: Installed? = null
    private val tiles = mutableStateOf<List<Tile>>(emptyList())
    private val home = mutableIntStateOf(0)

    // Apps installed, removed or updated while the Launcher is running.
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = OrderStore(this)
        onBackPressedDispatcher.addCallback(this) {} // Back on the home screen goes nowhere
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            LauncherScreen(
                tiles = tiles.value,
                home = home.intValue,
                onOpen = ::open,
                onAppInfo = ::showAppInfo,
                onMove = ::move,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while the Launcher was already showing (not when coming back from an App).
        val alreadyHome = hasWindowFocus() &&
            intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (alreadyHome) home.intValue++
    }

    private fun reload() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadInstalled(this@LauncherActivity) }
            installed = loaded
            val keys = TileOrder.arrange(store.load(), loaded.apps.mapValues { it.value.tile.label })
            store.save(keys) // from now on, new Apps go after these
            show(keys)
        }
    }

    private fun move(from: Int, to: Int) {
        val keys = TileOrder.move(tiles.value.map { it.key }, from, to)
        store.save(keys)
        show(keys)
    }

    private fun show(keys: List<String>) {
        val now = installed ?: return
        tiles.value = keys.mapNotNull { if (it == TileOrder.SETTINGS) now.settings else now.apps[it]?.tile }
    }

    private fun open(tile: Tile) {
        val intent = if (tile.key == TileOrder.SETTINGS) {
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            installed?.apps?.get(tile.key)?.launch ?: return
        }
        start(intent)
    }

    private fun showAppInfo(tile: Tile) {
        start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", tile.key, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Uninstalled a moment ago: the package receiver removes its Tile.
        }
    }
}
