// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.tv.TvContract
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URISyntaxException

/** The Ladrok TV home screen. */
class LauncherActivity : ComponentActivity() {
    private lateinit var store: Store
    private var installed: Installed? = null
    private val tiles = mutableStateOf<List<Tile>>(emptyList())
    private val shelves = mutableStateOf<List<Shelf>>(emptyList())
    private val channels = mutableStateOf<List<TvData.Channel>>(emptyList())
    private val shownChannels = mutableStateOf<Set<String>>(emptySet())
    private val home = mutableIntStateOf(0)
    private var loading: Job? = null

    // Apps installed, removed or updated while the Launcher is running.
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    // Apps adding to Watch Next or their Channels while the Launcher is showing.
    private val programs = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload(after = 500)
    }

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        shownChannels.value = store.channels()
        onBackPressedDispatcher.addCallback(this) {} // Back on the home screen goes nowhere
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        for (uri in listOf(TvContract.WatchNextPrograms.CONTENT_URI, TvContract.PreviewPrograms.CONTENT_URI, TvContract.Channels.CONTENT_URI)) {
            runCatching { contentResolver.registerContentObserver(uri, true, programs) }
        }
        // Ask once for Watch Next and Channels. Ladrok TV grants it ahead, so there it never asks.
        if (!TvData.allowed(this) && !store.askedPermission) {
            store.askedPermission = true
            askPermission.launch(TvData.PERMISSION)
        }
        setContent {
            LauncherScreen(
                shelves = shelves.value,
                tiles = tiles.value,
                appName = { installed?.apps?.get(it)?.tile?.label ?: it },
                home = home.intValue,
                onOpen = ::open,
                onOpenProgram = ::open,
                onAppInfo = ::showAppInfo,
                onMove = ::move,
                channelsOf = { tile ->
                    channels.value.filter { it.app == tile.key }
                        .map { ChannelChoice(it.key, it.name, it.key in shownChannels.value) }
                },
                onToggleChannel = { key ->
                    store.toggleChannel(key)
                    shownChannels.value = store.channels()
                    reload()
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        contentResolver.unregisterContentObserver(programs)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while the Launcher was already showing (not when coming back from an App).
        val alreadyHome = hasWindowFocus() &&
            intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (alreadyHome) home.intValue++
    }

    /** Reads the Apps, Watch Next and the switched-on Channels again, [after] ms from now. */
    private fun reload(after: Long = 0) {
        loading?.cancel()
        loading = lifecycleScope.launch {
            delay(after) // Apps often write several Programs in a row: read once they're done
            val shown = store.channels()
            val (apps, rows, all) = withContext(Dispatchers.IO) {
                val apps = loadInstalled(this@LauncherActivity)
                val all = TvData.channels(contentResolver)
                val watchNext = Shelf("watch-next", getString(R.string.watch_next), TvData.watchNext(contentResolver))
                val on = all.filter { it.key in shown && it.app in apps.apps }
                    .sortedBy { channel -> store.order().indexOf(channel.app).let { if (it < 0) Int.MAX_VALUE else it } }
                    .map { Shelf(it.key, "${apps.apps[it.app]?.tile?.label ?: it.app} · ${it.name}", TvData.programs(contentResolver, it)) }
                Triple(apps, Shelves.arrange(watchNext, on), all)
            }
            installed = apps
            channels.value = all
            val keys = TileOrder.arrange(store.order(), apps.apps.mapValues { it.value.tile.label })
            store.saveOrder(keys) // from now on, new Apps go after these
            shelves.value = rows
            show(keys)
        }
    }

    private fun move(from: Int, to: Int) {
        val keys = TileOrder.move(tiles.value.map { it.key }, from, to)
        store.saveOrder(keys)
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

    private fun open(program: Program) {
        val intent = program.intentUri?.let {
            try {
                Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
            } catch (_: URISyntaxException) {
                null
            }
        } ?: installed?.apps?.get(program.app)?.launch ?: return // no link to the Program: open its App
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        } catch (_: SecurityException) {
            // A Program link to something the App doesn't export: nothing to open.
        }
    }
}
