// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A pointer (an air mouse, or any mouse) on the Launcher: what it hovers takes the focus, so the
 * outline and the caption follow it, and a click or a long click does what OK or a long-press OK
 * does. Off while a menu is open, so hovering behind it doesn't pull the focus away.
 */
val LocalHoverFocus = compositionLocalOf { true }

/**
 * Hover focuses this item; a click calls [onClick], a long click [onLongClick] (an air mouse has
 * only the one button). Goes before the item's own focus target, like [focusRequester].
 */
@Composable
fun Modifier.pointer(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val focus = remember { FocusRequester() }
    val hover by rememberUpdatedState(LocalHoverFocus.current)
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val hasLongClick = onLongClick != null
    return focusRequester(focus)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Enter && hover) runCatching { focus.requestFocus() }
                }
            }
        }
        .pointerInput(hasLongClick) {
            // The item's own click handling, if it has any, runs first and consumes the press: then this sees nothing.
            detectTapGestures(
                onTap = { runCatching { focus.requestFocus() }; click() },
                onLongPress = if (hasLongClick) ({ runCatching { focus.requestFocus() }; longClick?.invoke() }) else null,
            )
        }
}

/** Calls [onTap] on a click without making the item focusable (the arrows never stop on it). */
@Composable
fun Modifier.onPointerTap(onTap: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    return pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
}

/**
 * Calls [onWheel] with -1 (up) or 1 (down) for each turn of the wheel, at most once every
 * [WHEEL_PAUSE] ms so that one flick of a touchpad or an air mouse turns one page, not five.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.onWheel(onWheel: (Int) -> Unit): Modifier {
    val wheel by rememberUpdatedState(onWheel)
    return pointerInput(Unit) {
        var last = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                event.changes.forEach { it.consume() }
                val now = event.changes.first().uptimeMillis
                if (dy != 0.0 && now - last >= WHEEL_PAUSE) {
                    last = now
                    wheel(if (dy > 0) 1 else -1)
                }
            }
        }
    }
}

private const val WHEEL_PAUSE = 300L
