package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalPlaybackStopped = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Runs [onReload] when the screen appears and each time it is resumed, which also does the first
 * load. The player reports its final position after the screen underneath has resumed, so
 * [onReload] runs again once that report is through.
 */
@Composable
fun ReloadOnResume(onReload: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = onReload)
    ObserveAsEvents(LocalPlaybackStopped.current) { onReload() }
}
