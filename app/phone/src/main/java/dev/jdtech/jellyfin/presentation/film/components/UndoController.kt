package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs destructive actions (cancelling, removing downloads) behind an Undo snackbar instead of
 * right away. The affected ids are hidden while the snackbar shows. The action is committed
 * when the snackbar times out, when another action replaces it, or when the screen goes away.
 */
@Stable
class UndoController(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val undoLabel: String,
) {
    var hiddenIds by mutableStateOf(emptySet<UUID>())
        private set

    fun run(ids: Set<UUID>, message: String, commit: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        hiddenIds = hiddenIds + ids
        scope.launch {
            // Resolve any earlier snackbar first; dismissing it commits that action.
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                try {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                } catch (e: CancellationException) {
                    // Leaving the screen counts as not undoing.
                    commit()
                    throw e
                }
            if (result == SnackbarResult.ActionPerformed) {
                hiddenIds = hiddenIds - ids
            } else {
                commit()
            }
        }
    }

    /**
     * Stops hiding ids that are no longer on screen once their action went through, so the same
     * item downloaded again later isn't hidden.
     */
    fun retainHidden(presentIds: Set<UUID>) {
        val retained = hiddenIds intersect presentIds
        if (retained.size != hiddenIds.size) hiddenIds = retained
    }
}

@Composable
fun rememberUndoController(snackbarHostState: SnackbarHostState): UndoController {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val undoLabel = stringResource(CoreR.string.undo)
    return remember(snackbarHostState, scope) {
        UndoController(snackbarHostState, scope, haptics, undoLabel)
    }
}
