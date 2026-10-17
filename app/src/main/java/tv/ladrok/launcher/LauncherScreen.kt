// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

// The Ladrok palette (ladrok-page), on pure black like the boot animation.
private val Black = Color(0xFF000000)
private val Raised = Color(0xFF121212)
private val Line = Color(0xFF262626)
private val Ink = Color(0xFFE4E4E4)
private val Muted = Color(0xFF8A8A8A)
private val Silver = Color(0xFFCFCFCF)

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private val DIGIT_KEYS = mapOf(
    Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5,
    Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4, Key.NumPad5 to 5,
    Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

/** A Channel in an App's Channels menu, and whether the viewer switched it on. */
class ChannelChoice(val key: String, val name: String, val shown: Boolean)

/**
 * The Launcher: Watch Next and switched-on Channels on the left, the Keypad on the right, a clock,
 * and the name of whatever has focus. The most recent Watch Next Program is shown large at the top,
 * to continue with one press; with nothing to continue, the left side shows the logo and the time.
 * Long-press OK on a Tile for Move, Channels, Watch Next, Updates (when [onShowInStore] is set) and App info, in a
 * menu beside the Tile; digits 1–9 open the Tile with that digit; Back hops between the rows and the
 * Keypad. [home] goes up each time Home is pressed while the Launcher is already showing.
 * With a pointer (an air mouse): hover focuses, click opens, a long click is the long press, the
 * wheel over the Keypad turns its pages, and in Move mode a click on a cell puts the Tile there.
 */
@Composable
fun LauncherScreen(
    shelves: List<Shelf>,
    tiles: List<Tile>,
    appName: (String) -> String,
    home: Int,
    appIcon: (String) -> ImageBitmap? = { null },
    onOpen: (Tile) -> Unit,
    onOpenProgram: (Program) -> Unit,
    onAppInfo: (Tile) -> Unit,
    onShowInStore: ((Tile) -> Unit)? = null,
    onMove: (from: Int, to: Int) -> Unit,
    channelsOf: (Tile) -> List<ChannelChoice>,
    onToggleChannel: (String) -> Unit,
    /** What the large card continues: by default the most recent in Watch Next ([Continue] picks it in the app). */
    continueWith: Program? = Shelves.latest(shelves),
    /** The banner of [continueWith]'s App, when the card shows just the App. */
    continueBanner: ImageBitmap? = null,
    /** Whether [Tile]'s Watch Next is shown, or null when its App has none to show or hide. */
    watchNextOf: (Tile) -> Boolean? = { null },
    onToggleWatchNext: (app: String) -> Unit = {},
    /** Takes a Program out of Watch Next until it is watched again. */
    onRemove: (Program) -> Unit = {},
) {
    var caption by remember { mutableStateOf("") }
    // Time left in the focused Program, on the right of the caption.
    var captionLeft by remember { mutableStateOf("") }
    val resources = LocalContext.current.resources
    val (spotlight, shelfRows) = remember(shelves, continueWith) { Shelves.spotlight(shelves, continueWith) }
    // Nothing to continue and no rows: the time, large, and the Keypad.
    val nothing = spotlight == null && shelfRows.isEmpty()
    var focusedTile by remember { mutableIntStateOf(-1) }
    var menuFor by remember { mutableStateOf<Tile?>(null) }
    var channelsFor by remember { mutableStateOf<Tile?>(null) }
    // A Watch Next Program whose menu (long-press OK) is open.
    var programMenu by remember { mutableStateOf<Program?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    // A cell (0–8) to focus once its page is drawn, and a counter so the same cell can be asked for twice.
    var focusCell by remember { mutableStateOf(0 to 0) }
    val cells = remember { List(Keypad.PER_PAGE) { FocusRequester() } }
    // Where each cell is on screen, for the menu that opens beside its Tile.
    val cellBounds = remember { arrayOfNulls<IntRect>(Keypad.PER_PAGE) }
    val firstProgram = remember { FocusRequester() }
    val rows = rememberLazyListState()
    val firstRow = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The Tile that had focus last, for Back from the rows.
    var lastTile by remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pages = Keypad.pages(tiles.size)
    if (page >= pages) page = pages - 1

    fun timeLeft(program: Program): String = program.left?.let(Shelves::minutesLeft)?.let { minutes ->
        if (minutes < 60) resources.getString(R.string.minutes_left, minutes)
        else resources.getString(R.string.hours_left, minutes / 60, minutes % 60)
    }.orEmpty()

    fun showCell(index: Int) {
        page = Keypad.pageOf(index)
        focusCell = index % Keypad.PER_PAGE to focusCell.second + 1
    }

    // Focus the first Program (or the first Tile) when the Launcher first fills, and on every Home.
    val ready = tiles.isNotEmpty()
    LaunchedEffect(ready, home) {
        if (!ready) return@LaunchedEffect
        menuFor = null
        channelsFor = null
        programMenu = null
        moving = null
        if (home > 0 || caption.isEmpty()) {
            page = 0
            withFrameNanos {}
            if (nothing || !runCatching { firstProgram.requestFocus() }.getOrDefault(false)) showCell(0)
        }
    }
    LaunchedEffect(focusCell) {
        if (focusCell.second == 0) return@LaunchedEffect
        withFrameNanos {} // the page's cells are drawn
        runCatching { cells[focusCell.first.coerceAtMost(lastCellOn(page, tiles.size))].requestFocus() }
    }

    val movingIndex = tiles.indexOfFirst { it.key == moving }
    LaunchedEffect(movingIndex) { if (movingIndex >= 0) showCell(movingIndex) }
    BackHandler(enabled = moving != null) { moving = null }
    // Back hops between the rows and the Keypad: one press instead of walking past every Program.
    BackHandler(enabled = moving == null && !nothing) {
        if (focusedTile >= 0) {
            scope.launch {
                rows.scrollToItem(0)
                firstRow.scrollToItem(0)
                withFrameNanos {} // the first Program is drawn
                runCatching { firstProgram.requestFocus() }
            }
        } else {
            showCell(lastTile.coerceAtMost(tiles.size - 1))
        }
    }

    fun keypadKey(event: KeyEvent): Boolean {
        val left = if (rtl) Key.DirectionRight else Key.DirectionLeft
        val right = if (rtl) Key.DirectionLeft else Key.DirectionRight
        if (movingIndex >= 0) {
            // In Move mode the arrows move the Tile instead of the focus, and OK puts it down.
            val step = when (event.key) {
                left -> -1
                right -> 1
                Key.DirectionUp -> -Keypad.COLUMNS
                Key.DirectionDown -> Keypad.COLUMNS
                else -> 0
            }
            return when {
                step != 0 -> { if (event.type == KeyEventType.KeyDown) onMove(movingIndex, movingIndex + step); true }
                event.key in OK_KEYS -> { if (event.type == KeyEventType.KeyUp) moving = null; true }
                else -> false
            }
        }
        if (event.type != KeyEventType.KeyDown || focusedTile < 0) return false
        // Off the Keypad's right edge: the next page. Off its left edge: the page before (or, on the first, the rows).
        val cell = focusedTile % Keypad.PER_PAGE
        val row = cell / Keypad.COLUMNS
        val column = cell % Keypad.COLUMNS
        return when {
            event.key == right && column == Keypad.COLUMNS - 1 && page < pages - 1 -> {
                page++; focusCell = row * Keypad.COLUMNS to focusCell.second + 1; true
            }
            event.key == left && column == 0 && page > 0 -> {
                page--; focusCell = row * Keypad.COLUMNS + Keypad.COLUMNS - 1 to focusCell.second + 1; true
            }
            else -> false
        }
    }

    val menuOpen = menuFor != null || channelsFor != null || programMenu != null
    CompositionLocalProvider(LocalHoverFocus provides !menuOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Black)
                .onPreviewKeyEvent { event ->
                    // Digits open the Tile with that digit on the page shown.
                    val digit = DIGIT_KEYS[event.key] ?: return@onPreviewKeyEvent false
                    if (moving == null && event.type == KeyEventType.KeyDown) {
                        Keypad.indexOf(page, digit, tiles.size)?.let { onOpen(tiles[it]) }
                    }
                    true
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding() // no-op on a TV; keeps clear of system bars elsewhere
                    .padding(horizontal = 36.dp, vertical = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    if (!nothing) Clock() // with nothing to continue, the time is large on the left
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Box(Modifier.weight(2f).fillMaxHeight().alpha(if (menuOpen) DIMMED else 1f)) {
                        if (nothing) {
                            // Nothing to continue: the Ladrok logo, faint, and the time.
                            Image(
                                painterResource(R.drawable.ladrok_logo),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Silver),
                                alpha = 0.14f,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).fillMaxHeight(0.45f),
                            )
                            BigClock(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 4.dp))
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            state = rows,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val onFocusProgram = { program: Program ->
                                caption = if (program.onlyApp) appName(program.app) else "${program.title} · ${appName(program.app)}"
                                captionLeft = timeLeft(program)
                                focusedTile = -1
                            }
                            if (spotlight != null) item(key = "spotlight") {
                                Spotlight(
                                    program = spotlight,
                                    appName = appName(spotlight.app),
                                    appIcon = appIcon(spotlight.app),
                                    timeLeft = timeLeft(spotlight),
                                    banner = continueBanner,
                                    first = firstProgram,
                                    keypad = cells[0],
                                    onOpen = onOpenProgram,
                                    onLongPress = { programMenu = it },
                                    onFocus = onFocusProgram,
                                )
                            }
                            itemsIndexed(shelfRows, key = { _, shelf -> shelf.key }) { row, shelf ->
                                ShelfRow(
                                    shelf = shelf,
                                    icon = shelf.app?.let(appIcon),
                                    small = spotlight != null,
                                    first = if (row == 0 && spotlight == null) firstProgram else null,
                                    state = if (row == 0) firstRow else rememberLazyListState(),
                                    onOpen = onOpenProgram,
                                    onLongPress = { programMenu = it },
                                    onFocus = onFocusProgram,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp)
                            .onPreviewKeyEvent(::keypadKey)
                            .onWheel { step ->
                                val to = (page + step).coerceIn(0, pages - 1)
                                if (moving == null && to != page) showCell(to * Keypad.PER_PAGE + focusedTile.coerceAtLeast(0) % Keypad.PER_PAGE)
                            },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        for (row in 0 until Keypad.COLUMNS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (column in 0 until Keypad.COLUMNS) {
                                    val cell = row * Keypad.COLUMNS + column
                                    val index = page * Keypad.PER_PAGE + cell
                                    val tile = tiles.getOrNull(index)
                                    if (tile == null) {
                                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                                    } else key(tile.key) {
                                        val isSettings = tile.key == TileOrder.SETTINGS
                                        val menuTile = (menuFor ?: channelsFor)?.key
                                        // In Move mode (only a pointer gets here: OK is taken before) a click puts the Tile in this cell.
                                        val onClick = {
                                            if (movingIndex >= 0) { onMove(movingIndex, index); moving = null } else onOpen(tile)
                                        }
                                        val onLongClick = if (isSettings || moving != null) null else ({ menuFor = tile })
                                        KeypadCell(
                                            tile = tile,
                                            digit = Keypad.digitOf(index),
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            modifier = Modifier
                                                .weight(1f)
                                                .pointer(onClick, onLongClick)
                                                .alpha(if (menuTile != null && menuTile != tile.key) DIMMED else 1f)
                                                .onGloballyPositioned { cellBounds[cell] = it.boundsInWindow().roundToIntRect() }
                                                .focusRequester(cells[cell])
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        focusedTile = index; lastTile = index; caption = tile.label; captionLeft = ""
                                                    }
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val text = if (movingIndex >= 0) stringResource(R.string.move_hint, tiles[movingIndex].label) else caption
                    Text(
                        text, color = Muted, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("caption"),
                    )
                    if (movingIndex < 0 && captionLeft.isNotEmpty()) {
                        Text(
                            captionLeft, color = Muted, fontSize = 20.sp, maxLines = 1,
                            modifier = Modifier.padding(start = 24.dp).testTag("left"),
                        )
                    }
                    if (pages > 1) {
                        Text(
                            "${page + 1} / $pages", color = Muted, fontSize = 20.sp,
                            // A click turns the page, for a pointer without a wheel.
                            modifier = Modifier.padding(start = 24.dp).testTag("page")
                                .onPointerTap { if (moving == null) showCell((page + 1) % pages * Keypad.PER_PAGE) },
                        )
                    }
                }
            }
        }
    }

    fun boundsOf(tile: Tile): IntRect? = tiles.indexOfFirst { it.key == tile.key }
        .takeIf { it >= 0 && Keypad.pageOf(it) == page }
        ?.let { cellBounds[it % Keypad.PER_PAGE] }

    menuFor?.let { tile ->
        val channels = channelsOf(tile)
        Menu(
            title = tile.label,
            beside = boundsOf(tile),
            onDismiss = { menuFor = null },
            items = listOfNotNull(
                stringResource(R.string.move) to { menuFor = null; moving = tile.key },
                if (channels.isNotEmpty()) {
                    "${stringResource(R.string.channels)}\t${stringResource(R.string.channels_on, channels.count { it.shown }, channels.size)}" to
                        { menuFor = null; channelsFor = tile }
                } else null,
                watchNextOf(tile)?.let { shown ->
                    "${stringResource(R.string.watch_next)}\t${stringResource(if (shown) R.string.channel_on else R.string.channel_off)}" to
                        { onToggleWatchNext(tile.key) }
                },
                onShowInStore?.let { show ->
                    "${stringResource(R.string.updates)}\t${stringResource(R.string.updates_where)}" to { menuFor = null; show(tile) }
                },
                stringResource(R.string.app_info) to { menuFor = null; onAppInfo(tile) },
            ),
        )
    }
    programMenu?.let { program ->
        val app = appName(program.app)
        Menu(
            title = program.title,
            beside = null,
            onDismiss = { programMenu = null },
            items = listOf(
                stringResource(R.string.remove) to { programMenu = null; onRemove(program) },
                stringResource(R.string.hide_app, app) to { programMenu = null; onToggleWatchNext(program.app) },
            ),
        )
    }
    channelsFor?.let { tile ->
        val on = stringResource(R.string.channel_on)
        val off = stringResource(R.string.channel_off)
        Menu(
            title = "${tile.label} · ${stringResource(R.string.channels)}",
            beside = boundsOf(tile),
            onDismiss = { channelsFor = null },
            items = channelsOf(tile).map { choice ->
                "${choice.name}\t${if (choice.shown) on else off}" to { onToggleChannel(choice.key) }
            },
        )
    }
}

/** The last cell (0–8) that has a Tile on [page]. */
private fun lastCellOn(page: Int, tiles: Int) =
    (tiles - 1 - page * Keypad.PER_PAGE).coerceIn(0, Keypad.PER_PAGE - 1)

/** How much of the screen fades while a Tile's menu is open: all but that Tile. */
private const val DIMMED = 0.35f

/** The Program to continue: large, with its title, the time left and its App beside it. */
@Composable
private fun Spotlight(
    program: Program,
    appName: String,
    appIcon: ImageBitmap?,
    timeLeft: String,
    banner: ImageBitmap?,
    first: FocusRequester,
    keypad: FocusRequester,
    onOpen: (Program) -> Unit,
    onLongPress: (Program) -> Unit,
    onFocus: (Program) -> Unit,
) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ProgramCard(
            program = program,
            width = 340.dp,
            banner = banner,
            onClick = { onOpen(program) },
            onLongClick = { onLongPress(program) },
            modifier = Modifier
                .focusRequester(first)
                .focusProperties { end = keypad } // towards the Keypad: its first Tile, not whichever is level with the card
                .onFocusChanged { if (it.hasFocus) onFocus(program) },
        )
        Column(Modifier.height(191.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Just the App (it doesn't say what was playing): its name, and that this is where the viewer was.
            Text(if (program.onlyApp) appName else program.title, color = Ink, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (program.onlyApp) Text(stringResource(R.string.continue_app), color = Muted, fontSize = 16.sp)
            else if (timeLeft.isNotEmpty()) Text(timeLeft, color = Muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (appIcon != null) Image(appIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(appName, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    icon: ImageBitmap?,
    small: Boolean,
    first: FocusRequester?,
    state: LazyListState,
    onOpen: (Program) -> Unit,
    onLongPress: (Program) -> Unit,
    onFocus: (Program) -> Unit,
) {
    Column {
        // A Channel's row carries its App's icon: "Jellyfin · Recently added".
        Row(Modifier.padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(16.dp))
            Text(shelf.title, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(shelf.programs, key = { _, program -> program.key }) { index, program ->
                ProgramCard(
                    program = program,
                    width = if (small) 150.dp else 200.dp,
                    onClick = { onOpen(program) },
                    onLongClick = { onLongPress(program) },
                    modifier = Modifier
                        .then(if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { if (it.hasFocus) onFocus(program) },
                )
            }
        }
    }
}

private val shape = RoundedCornerShape(8.dp)
private val outline = Border(BorderStroke(2.dp, Silver), shape = shape)

@Composable
private fun tileColors() = ClickableSurfaceDefaults.colors(
    containerColor = Raised,
    contentColor = Ink,
    focusedContainerColor = Raised,
    focusedContentColor = Ink,
    pressedContainerColor = Raised,
    pressedContentColor = Ink,
)

@Composable
private fun ProgramCard(
    program: Program,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
    banner: ImageBitmap? = null,
    onLongClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        // Only Watch Next can be taken out; a Channel is switched off from its App's Tile.
        onLongClick = if (program.inWatchNext) onLongClick else null,
        modifier = modifier.pointer(onClick, if (program.inWatchNext) onLongClick else null).width(width).aspectRatio(16f / 9f).semantics { contentDescription = program.title.ifEmpty { program.app } },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = tileColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = outline, pressedBorder = outline),
    ) {
        if (program.onlyApp) {
            // Just the App: its banner, or the Ladrok logo when it has none.
            if (banner != null) {
                Image(banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    painterResource(R.drawable.ladrok_logo), contentDescription = null, colorFilter = ColorFilter.tint(Silver),
                    alpha = 0.5f, modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.5f),
                )
            }
        } else {
            // The title shows until (or unless) the poster loads over it.
            Text(
                program.title, color = Ink, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
            if (program.poster != null) {
                AsyncImage(program.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            program.progress?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it).height(3.dp).background(Silver))
            }
        }
    }
}

@Composable
private fun KeypadCell(tile: Tile, digit: Int, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.aspectRatio(1f).semantics { contentDescription = tile.label },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = tileColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = outline, pressedBorder = outline),
    ) {
        val icon = tile.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f))
        } else {
            Text(
                tile.label, color = Ink, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
        }
        Text(
            digit.toString(), color = Muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
        )
    }
}

/**
 * A short list of choices under [title], opened beside the Tile at [beside] (in the middle of the
 * screen when that isn't known). A label may carry a note on the right after a tab ("Trending\tOn").
 */
@Composable
private fun Menu(title: String, beside: IntRect?, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 16.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(beside, gap) { BesideTile(beside, gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalHoverFocus provides true) {
            val first = remember { FocusRequester() }
            Column(
                Modifier
                    .width(320.dp)
                    .background(Raised, RoundedCornerShape(12.dp))
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .ignoreHeldOk(),
            ) {
                Text(
                    title, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                )
                items.forEachIndexed { index, (label, onClick) ->
                    MenuItem(label, onClick, if (index == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos {}
                runCatching { first.requestFocus() }
            }
        }
    }
}

/**
 * Swallows OK (centre, Enter) until it is pressed again inside: the menu opens while OK is still
 * held, and without this its release (or the key's repeats) would choose the first item.
 */
@Composable
private fun Modifier.ignoreHeldOk(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            !ok -> false
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
            else -> !armed
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.pointer(onClick).fillMaxWidth(),
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
        val name = label.substringBefore('\t')
        val note = label.substringAfter('\t', "")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (note.isNotEmpty()) Text(note, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** Beside the Tile at [tile], on the side towards the rows, kept on screen; centred when [tile] is null. */
private class BesideTile(private val tile: IntRect?, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        if (tile == null) return IntOffset(maxX / 2, maxY / 2)
        val x = if (layoutDirection == LayoutDirection.Ltr) tile.left - gap - popupContentSize.width else tile.right + gap
        return IntOffset(x.coerceIn(0, maxX), tile.top.coerceIn(0, maxY))
    }
}

/** The date and the time, in the system's 12- or 24-hour format. */
@Composable
private fun Clock() {
    val (date, time) = now()
    Text("$date   $time", color = Muted, fontSize = 20.sp)
}

/** The time, large, and the date under it: for when there's nothing to continue. */
@Composable
private fun BigClock(modifier: Modifier) {
    val (date, time) = now(longDate = true)
    Column(modifier) {
        Text(time, color = Ink, fontSize = 88.sp, fontWeight = FontWeight.Light, lineHeight = 88.sp)
        Text(date, color = Muted, fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The date ("Thu 16 Oct", or "Thursday 16 October") and time in the viewer's locale, updated on the minute. */
@Composable
private fun now(longDate: Boolean = false): Pair<String, String> {
    val context = LocalContext.current
    val pattern = if (longDate) "EEEEdMMMM" else "EEEdMMM"
    val value by produceState("" to "", longDate) {
        while (true) {
            val date = Date()
            val locale = context.resources.configuration.locales[0]
            value = DateFormat.format(DateFormat.getBestDateTimePattern(locale, pattern), date).toString() to
                DateFormat.getTimeFormat(context).format(date)
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    return value
}
