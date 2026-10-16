// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * What the viewer did last outside the Launcher, for the large card ([Continue]): the App they
 * opened from the Launcher and stayed in, and what an App's player last said it was playing.
 */
class Recent(private val context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

    /** The viewer opens [app] from the Launcher now. */
    fun opened(app: String) {
        prefs.edit().putString(OPENED, app).putLong(OPENED_AT, System.currentTimeMillis()).apply()
    }

    /** The viewer is back on the home screen: the App they were in becomes the [Visit], if they stayed. */
    fun back() {
        val app = prefs.getString(OPENED, null) ?: return
        val started = prefs.getLong(OPENED_AT, 0)
        val edit = prefs.edit().remove(OPENED).remove(OPENED_AT)
        if (System.currentTimeMillis() - started >= Continue.MIN_VISIT) edit.putString(VISIT, app).putLong(VISIT_AT, started)
        edit.apply()
    }

    fun visit(): Visit? = prefs.getString(VISIT, null)?.let { Visit(it, prefs.getLong(VISIT_AT, 0)) }

    fun playing(): Playing? = prefs.getString(PLAYING, null)?.let {
        Playing(
            app = it,
            at = prefs.getLong(PLAYING_AT, 0),
            title = prefs.getString(PLAYING_TITLE, null).orEmpty(),
            poster = prefs.getString(PLAYING_POSTER, null)?.takeIf { path -> File(path).exists() }?.let { path -> "file://$path" },
            position = prefs.getLong(PLAYING_POSITION, 0),
            duration = prefs.getLong(PLAYING_DURATION, 0),
        )
    }

    /** Keeps what [controller]'s App is playing or paused on. Only the newest one is kept. */
    fun save(controller: MediaController) {
        val app = controller.packageName
        if (app == context.packageName) return
        val state = controller.playbackState ?: return
        if (state.state != PlaybackState.STATE_PLAYING && state.state != PlaybackState.STATE_PAUSED) return
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) return
        val position = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        } else {
            state.position
        }
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val known = prefs.getString(PLAYING, null) == app && prefs.getString(PLAYING_TITLE, null) == title
        // The poster is written once per title, not on every position update.
        val poster = if (known) prefs.getString(PLAYING_POSTER, null) else art?.let { write(app, it) }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        prefs.edit()
            .putString(PLAYING, app)
            .putLong(PLAYING_AT, System.currentTimeMillis())
            .putString(PLAYING_TITLE, title)
            .putString(PLAYING_POSTER, poster)
            .putLong(PLAYING_POSITION, position.coerceAtLeast(0))
            .putLong(PLAYING_DURATION, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0))
            .apply()
    }

    /** Writes the poster under a new name each time, so the image loader never shows an old one. */
    private fun write(app: String, art: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "playing").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val scaled = if (art.width > POSTER_WIDTH) {
            Bitmap.createScaledBitmap(art, POSTER_WIDTH, art.height * POSTER_WIDTH / art.width, true)
        } else {
            art
        }
        File(dir, "$app-${System.currentTimeMillis()}.jpg").also { file ->
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }.path
    }.getOrNull()

    private companion object {
        const val OPENED = "opened"
        const val OPENED_AT = "opened_at"
        const val VISIT = "visit"
        const val VISIT_AT = "visit_at"
        const val PLAYING = "playing"
        const val PLAYING_AT = "playing_at"
        const val PLAYING_TITLE = "playing_title"
        const val PLAYING_POSTER = "playing_poster"
        const val PLAYING_POSITION = "playing_position"
        const val PLAYING_DURATION = "playing_duration"
        const val POSTER_WIDTH = 640
    }
}

/**
 * Follows the players of every App (their media sessions) and keeps what they play in [recent].
 * Needs MEDIA_CONTENT_CONTROL, which Ladrok TV grants the Launcher as a privileged App; without
 * it this does nothing and the large card falls back to the App itself.
 */
class Players(private val context: Context, private val recent: Recent, private val onChange: () -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val followed = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    val allowed get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!allowed || manager == null) return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessions, null, handler)
            follow(manager.getActiveSessions(null))
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessions) }
        followed.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        followed.clear()
    }

    /** Saves what the current players show now, e.g. when the viewer comes back to the home screen. */
    fun snapshot() {
        if (!allowed || manager == null) return
        runCatching { manager.getActiveSessions(null).forEach(recent::save) }
    }

    private fun follow(controllers: List<MediaController>) {
        val now = controllers.associateBy { it.sessionToken }
        (followed.keys - now.keys).forEach { token ->
            followed.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        for ((token, controller) in now) {
            if (token in followed) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = changed(controller)
                override fun onPlaybackStateChanged(state: PlaybackState?) = changed(controller)
            }
            controller.registerCallback(callback, handler)
            followed[token] = controller to callback
            recent.save(controller)
        }
    }

    private fun changed(controller: MediaController) {
        recent.save(controller)
        onChange()
    }

    companion object {
        const val PERMISSION = "android.permission.MEDIA_CONTENT_CONTROL"
    }
}

/** [app]'s TV banner (the wide image on its Tile in other launchers), or null when it has none. */
fun loadBanner(context: Context, app: String, component: ComponentName?): Bitmap? = runCatching {
    val pm = context.packageManager
    val banner = component?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(app)
    banner?.toBitmap(480, 270)
}.getOrNull()
