package dev.jdtech.jellyfin.ce.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * CE: lets the D-pad leave a text field. A focused text field keeps Up and Down for itself, so with
 * a remote focus never reaches the fields and buttons above and below it (the login form, the
 * server address). Up and Down hide the keyboard and move focus; Left and Right still move the
 * cursor. When there's nothing to move to, the field gets the key as before.
 */
fun Modifier.dpadLeavesTextField(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
): Modifier = onPreviewKeyEvent { event ->
    val direction =
        when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return@onPreviewKeyEvent false
        }
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    focusManager.moveFocus(direction).also { moved -> if (moved) keyboardController?.hide() }
}
