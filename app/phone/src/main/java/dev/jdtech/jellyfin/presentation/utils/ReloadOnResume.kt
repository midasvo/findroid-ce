package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalPlaybackStopped = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Runs [onReload] when the screen appears, which also does the first load, and when it is resumed
 * and [shouldReload] says it is out of date (#251): a plain back navigation reloads nothing. The
 * player reports its final position after the screen underneath has resumed, so [onReload] runs
 * again then. [shouldReload] stays false while that report is still due, so it doesn't load twice.
 */
@Composable
fun ReloadOnResume(shouldReload: () -> Boolean, onReload: () -> Unit) {
    val currentShouldReload by rememberUpdatedState(shouldReload)
    val currentOnReload by rememberUpdatedState(onReload)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (currentShouldReload()) currentOnReload()
    }
    ObserveAsEvents(LocalPlaybackStopped.current) { currentOnReload() }
}
