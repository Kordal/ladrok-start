// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri

/** Reads Watch Next and Channels, which Apps publish in the system's TvProvider. */
object TvData {
    /**
     * Lets the Launcher read other Apps' Watch Next and Channels (Android 14+; READ_EPG_DATA alone
     * is granted at install but no longer enough). Granted once, by the viewer or ahead by Ladrok TV.
     */
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"

    /** A Channel some App publishes, whether or not the viewer switched it on. */
    class Channel(val id: Long, val app: String, val name: String) {
        /** Stable across App updates, which may recreate the Channel under a new id. */
        val key get() = "$app/$name"
    }

    fun allowed(context: Context) =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Watch Next, most recently watched first. */
    fun watchNext(resolver: ContentResolver): List<Program> = resolver.read(
        TvContract.WatchNextPrograms.CONTENT_URI,
        PROGRAM_COLUMNS + TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        sort = "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
    ) { program("w") }

    /** Every Channel that Apps publish for the home screen. */
    fun channels(resolver: ContentResolver): List<Channel> = resolver.read(
        TvContract.Channels.CONTENT_URI,
        arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_TYPE,
        ),
        // No selection: TvProvider refuses one from a Launcher without the system-only permission.
    ) { if (getString(3) == TvContract.Channels.TYPE_PREVIEW) Channel(getLong(0), getString(1).orEmpty(), getString(2).orEmpty()) else null }
        .filter { it.app.isNotEmpty() && it.name.isNotEmpty() }

    /** The Programs in [channel], in the App's order. */
    fun programs(resolver: ContentResolver, channel: Channel): List<Program> = resolver.read(
        TvContract.buildPreviewProgramsUriForChannel(channel.id),
        PROGRAM_COLUMNS,
    ) { program("p") }

    private val PROGRAM_COLUMNS = arrayOf(
        TvContract.PreviewPrograms._ID,
        TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
        TvContract.PreviewPrograms.COLUMN_TITLE,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        TvContract.PreviewPrograms.COLUMN_BROWSABLE,
    )

    private fun Cursor.program(prefix: String): Program? {
        if (!isNull(7) && getInt(7) == 0) return null // removed by the viewer
        val position = if (isNull(5)) 0L else getLong(5)
        val duration = if (isNull(6)) 0L else getLong(6)
        return Program(
            key = "$prefix${getLong(0)}",
            app = getString(1).orEmpty(),
            title = getString(2).orEmpty(),
            poster = getString(3),
            intentUri = getString(4),
            progress = if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
            left = if (position > 0 && duration > position) duration - position else null,
            at = if (columnCount > 8 && !isNull(8)) getLong(8) else null, // Watch Next's last engagement
        )
    }

    /** Rows of [uri] read with [row]; nothing when the provider is missing or the permission isn't granted. */
    private fun <T : Any> ContentResolver.read(
        uri: Uri,
        columns: Array<String>,
        sort: String? = null,
        row: Cursor.() -> T?,
    ): List<T> = try {
        query(uri, columns, null, null, sort)?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.row()?.let(::add) }
        }.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList() // no TvProvider on this device
    }
}
