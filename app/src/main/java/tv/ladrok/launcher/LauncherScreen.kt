// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

// The Ladrok palette (ladrok-page), on pure black like the boot animation.
private val Black = Color(0xFF000000)
private val Raised = Color(0xFF121212)
private val Line = Color(0xFF262626)
private val Ink = Color(0xFFE4E4E4)
private val Muted = Color(0xFF8A8A8A)
private val Silver = Color(0xFFCFCFCF)

const val COLUMNS = 5

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * The Launcher: a grid of Tiles, a clock, and the name of the focused Tile. Long-press OK on an
 * App's Tile for Move and App info. [home] goes up each time Home is pressed while the Launcher
 * is already showing.
 */
@Composable
fun LauncherScreen(
    tiles: List<Tile>,
    home: Int,
    onOpen: (Tile) -> Unit,
    onAppInfo: (Tile) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var focused by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Tile?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val first = remember { FocusRequester() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Focus the first Tile when the Tiles first appear, and again whenever Home is pressed.
    val hasTiles = tiles.isNotEmpty()
    LaunchedEffect(hasTiles, home) {
        if (!hasTiles) return@LaunchedEffect
        menuFor = null
        moving = null
        if (home > 0 || focused == null) {
            grid.scrollToItem(0)
            withFrameNanos {} // the first Tile is laid out after the scroll
            runCatching { first.requestFocus() }
        }
    }

    val movingIndex = tiles.indexOfFirst { it.key == moving }
    LaunchedEffect(movingIndex) {
        if (movingIndex >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == movingIndex }) {
            grid.scrollToItem(movingIndex)
        }
    }
    BackHandler(enabled = moving != null) { moving = null }

    Box(Modifier.fillMaxSize().background(Black)) {
        // The Ladrok logo, faint behind the Tiles: there when the screen is empty, quiet when it isn't.
        Image(
            painterResource(R.drawable.ladrok_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Silver),
            alpha = 0.08f,
            modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.45f),
        )
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding() // no-op on a TV; keeps clear of system bars elsewhere
                .padding(horizontal = 36.dp, vertical = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                Clock()
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = grid,
                // Room for the focused Tile to grow without being clipped.
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        // In Move mode the arrows move the Tile instead of the focus, and OK puts it down.
                        if (movingIndex < 0) return@onPreviewKeyEvent false
                        val step = when (event.key) {
                            Key.DirectionLeft -> if (rtl) 1 else -1
                            Key.DirectionRight -> if (rtl) -1 else 1
                            Key.DirectionUp -> -COLUMNS
                            Key.DirectionDown -> COLUMNS
                            else -> 0
                        }
                        when {
                            step != 0 -> {
                                if (event.type == KeyEventType.KeyDown) onMove(movingIndex, movingIndex + step)
                                true
                            }
                            event.key in OK_KEYS -> {
                                if (event.type == KeyEventType.KeyUp) moving = null
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                itemsIndexed(tiles, key = { _, tile -> tile.key }) { index, tile ->
                    val isSettings = tile.key == TileOrder.SETTINGS
                    TileView(
                        tile = tile,
                        onClick = { onOpen(tile) },
                        onLongClick = if (isSettings || moving != null) null else ({ menuFor = tile }),
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.hasFocus) focused = tile.key },
                    )
                }
            }
            val caption = when {
                movingIndex >= 0 -> stringResource(R.string.move_hint, tiles[movingIndex].label)
                else -> tiles.firstOrNull { it.key == focused }?.label.orEmpty()
            }
            Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(caption, color = Muted, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("caption"))
            }
        }
    }

    menuFor?.let { tile ->
        Menu(
            onMove = { menuFor = null; moving = tile.key },
            onAppInfo = { menuFor = null; onAppInfo(tile) },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun TileView(tile: Tile, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val outline = Border(BorderStroke(2.dp, Silver), shape = shape)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .semantics { contentDescription = tile.label },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Raised,
            contentColor = Ink,
            focusedContainerColor = Raised,
            focusedContentColor = Ink,
            pressedContainerColor = Raised,
            pressedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = outline, pressedBorder = outline),
    ) {
        val banner = tile.banner
        if (banner != null) {
            Image(banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // No banner (and the Settings Tile): the icon, if any, over the name.
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tile.icon?.let {
                    Image(it, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                }
                Text(tile.label, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Menu(onMove: () -> Unit, onAppInfo: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(Modifier.width(280.dp).background(Raised, RoundedCornerShape(12.dp)).padding(8.dp)) {
            MenuItem(stringResource(R.string.move), onMove, Modifier.focusRequester(first))
            MenuItem(stringResource(R.string.app_info), onAppInfo)
        }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Raised,
            contentColor = Muted,
            focusedContainerColor = Line,
            focusedContentColor = Ink,
            pressedContainerColor = Line,
            pressedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Text(label, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** The time, in the system's 12- or 24-hour format, updated on the minute. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val time by produceState("") {
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    Text(time, color = Muted, fontSize = 20.sp)
}
