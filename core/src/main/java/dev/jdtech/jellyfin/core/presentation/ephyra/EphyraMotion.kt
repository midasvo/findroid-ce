package dev.jdtech.jellyfin.core.presentation.ephyra

import android.content.res.Resources
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Motion tokens, docs/identity/VISION.md §6. Durations in ms. */
object EphyraMotion {
    /** Icon state, chip select, glow fade-in. */
    const val DURATION_SHORT = 200
    /** Screen enter, sheet open, list item appear. */
    const val DURATION_MEDIUM = 350
    /** Hero/backdrop crossfade, TV hero swap. */
    const val DURATION_LONG = 700
    /** Glow fade-out. */
    const val DURATION_GLOW_OUT = 150

    val EasingEnter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f) // M3 EmphasizedDecelerate
    val EasingExit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f) // M3 EmphasizedAccelerate
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f) // M3 Standard

    /** 8 dp upward drift on screen enter. */
    val EnterDrift = 8.dp
}

/**
 * The screen transition: the new screen fades in and drifts up [EphyraMotion.EnterDrift] into
 * place, the old one only fades out. No horizontal slide.
 *
 * The drift is converted with the display's density when a transition starts, so the spec can be a
 * plain value for `NavDisplay`'s transition lambdas.
 */
fun ephyraScreenTransition(): ContentTransform =
    (fadeIn(tween(EphyraMotion.DURATION_MEDIUM, easing = EphyraMotion.EasingEnter)) +
        slideInVertically(tween(EphyraMotion.DURATION_MEDIUM, easing = EphyraMotion.EasingEnter)) {
            (EphyraMotion.EnterDrift.value * Resources.getSystem().displayMetrics.density)
                .roundToInt()
        }) togetherWith fadeOut(tween(EphyraMotion.DURATION_SHORT, easing = EphyraMotion.EasingExit))

/**
 * True when the system animator duration scale is 0 (Developer options → Animator duration scale
 * off), read from the same [MotionDurationScale] Compose follows, so it updates while the app runs.
 *
 * Compose already plays every animation in a single frame then, the token-based ones included. This
 * is for what that leaves moving: the TV focus scale, which turns into the ring alone, and Coil's
 * crossfade, which keeps its own clock.
 */
@Composable
fun reducedMotion(): Boolean =
    rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f

/** Coil's backdrop crossfade: [EphyraMotion.DURATION_LONG], or 0 (none) under [reducedMotion]. */
@Composable fun backdropCrossfadeMillis(): Int = if (reducedMotion()) 0 else EphyraMotion.DURATION_LONG
