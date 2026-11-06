package dev.jdtech.jellyfin.ce.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * CE (ADR 0003): the element that last had focus on a screen, as a `(row, column)` plus an
 * optional item key.
 *
 * Remember it with [rememberFocusPosition], so Navigation 3 keeps it per back-stack entry. Mark each
 * focusable element with [focusPosition], and call [RestoreFocus] once per screen. On the first
 * visit that focuses the default position, and on a return the saved one.
 *
 * Writing the position on focus changes doesn't recompose anything: it's plain fields, read only
 * when the state is saved and when focus is restored.
 */
@Stable
class FocusPosition internal constructor(row: Int, column: Int, key: String?) {
    var row: Int = row
        private set

    var column: Int = column
        private set

    var key: String? = key
        private set

    private val requesters = mutableMapOf<Pair<Int, Int>, FocusRequester>()

    private var defaultRow = row
    private var defaultColumn = column

    internal fun setDefault(row: Int, column: Int) {
        defaultRow = row
        defaultColumn = column
    }

    fun save(row: Int, column: Int, key: String? = null) {
        this.row = row
        this.column = column
        this.key = key
    }

    /**
     * Points the saved position in [row] at the item with the saved key, for lists that can change
     * between visits. If the key is gone, it keeps the saved index, clamped to [count].
     */
    fun resolve(row: Int, count: Int, keyAt: (index: Int) -> String?) {
        if (this.row != row || count <= 0) return
        val savedKey = key
        val byKey = savedKey?.let { (0 until count).firstOrNull { index -> keyAt(index) == it } }
        column = byKey ?: column.coerceIn(0, count - 1)
    }

    /** Requests focus for the saved position. Returns false when that element isn't laid out. */
    fun requestFocus(): Boolean =
        try {
            requesters[row to column]?.requestFocus(FocusDirection.Enter) ?: false
        } catch (_: IllegalStateException) {
            requesters[row to column]?.requestFocus() ?: false
        }

    /** FocusRequester for the saved position, if one is currently registered. */
    fun currentRequester(): FocusRequester? = requesters[row to column]

    /** Moves back to the default element, for when the saved one is gone. */
    internal fun resetToDefault() = save(defaultRow, defaultColumn)

    internal fun register(row: Int, column: Int, requester: FocusRequester) {
        requesters[row to column] = requester
    }

    internal fun unregister(row: Int, column: Int, requester: FocusRequester) {
        if (requesters[row to column] === requester) requesters.remove(row to column)
    }

    companion object {
        val Saver =
            listSaver<FocusPosition, Any?>(
                save = { listOf(it.row, it.column, it.key) },
                restore = { FocusPosition(it[0] as Int, it[1] as Int, it[2] as String?) },
            )
    }
}

/**
 * Remembers the focus position of a screen per back-stack entry. [row] and [column] are the
 * default element, focused on the first visit.
 */
@Composable
fun rememberFocusPosition(row: Int = 0, column: Int = 0): FocusPosition =
    rememberSaveable(saver = FocusPosition.Saver) { FocusPosition(row, column, null) }
        .also { it.setDefault(row, column) }

/**
 * Marks this element as `(row, column)` of [position]: it saves the position when the element (or
 * a child) gains focus, and lets [RestoreFocus] focus it. [key] identifies the item in lists that
 * can change, see [FocusPosition.resolve].
 */
@Composable
fun Modifier.focusPosition(
    position: FocusPosition,
    row: Int,
    column: Int,
    key: String? = null,
): Modifier {
    val requester = remember { FocusRequester() }
    DisposableEffect(position, row, column, requester) {
        position.register(row, column, requester)
        onDispose { position.unregister(row, column, requester) }
    }
    return this.focusRequester(requester).onFocusChanged {
        if (it.hasFocus) position.save(row, column, key)
    }
}

/**
 * Puts focus on the saved position once each time the screen enters composition: the default
 * element on the first visit, the last focused one after Back.
 *
 * @param ready true once the content is there (for example the first page of a list).
 * @param prepare runs right before focusing: resolve keys and scroll the saved item into view, so
 *   the request doesn't go to an item that isn't laid out.
 */
@Composable
fun RestoreFocus(
    position: FocusPosition,
    ready: Boolean,
    prepare: suspend (FocusPosition) -> Unit = {},
) {
    // Not saveable on purpose: it has to run again after the screen comes back.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (!ready || restored) return@LaunchedEffect
        prepare(position)
        // A scroll in prepare only composes the item on the next frame.
        repeat(RESTORE_ATTEMPTS) { attempt ->
            withFrameNanos {}
            if (position.requestFocus()) {
                restored = true
                return@LaunchedEffect
            }
            // Halfway, give up on the saved element (it may be gone) and use the default one.
            if (attempt == RESTORE_ATTEMPTS / 2) position.resetToDefault()
        }
        restored = true
    }
}

private const val RESTORE_ATTEMPTS = 60
