package dev.jdtech.jellyfin.ce.presentation.player

import android.graphics.BlurMaskFilter
import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.ce.ui.focus.EphyraFocusGlowAlpha

/**
 * CE (#126): the TV seek bar. It takes focus like any button. While focused, Left/Right move a
 * pending position by the seek steps (faster while held, see [TvPlayerKeyHandler.holdSeekMs]), and
 * OK seeks there. Leaving the bar without OK drops the pending position.
 *
 * @param pendingMs the position being picked, or null; hoisted so the time label can show it.
 */
@Composable
fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    backStepMs: Long,
    forwardStepMs: Long,
    pendingMs: Long?,
    onPendingChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    chapterMarkers: List<Float> = emptyList(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val height by animateDpAsState(targetValue = if (isFocused) 16.dp else 8.dp)
    var holdStart by remember { mutableStateOf(0L) }
    var lastRepeat by remember { mutableStateOf(0L) }

    LaunchedEffect(isFocused) { if (!isFocused) onPendingChange(null) }

    val shown = pendingMs ?: positionMs
    val fraction = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Progress is alive: the played track and the thumb are primary (VISION §1, §3.4).
    val color = MaterialTheme.colorScheme.primary
    // The thumb's halo (VISION §6): primary at EphyraFocusGlowAlpha, blurred 20 dp.
    val density = LocalDensity.current
    val haloPaint =
        remember(color, density) {
            Paint().apply {
                this.color = color.copy(alpha = EphyraFocusGlowAlpha)
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(with(density) { 20.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
        }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 4.dp)
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val keyCode = event.key.nativeKeyCode
                    val forward =
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> true
                            KeyEvent.KEYCODE_DPAD_LEFT -> false
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                // OK only means something while a position is pending.
                                if (pendingMs == null) return@onKeyEvent false
                                if (native.action == KeyEvent.ACTION_UP) {
                                    onSeek(pendingMs)
                                    onPendingChange(null)
                                }
                                onInteraction()
                                return@onKeyEvent true
                            }
                            else -> return@onKeyEvent false
                        }
                    if (native.action != KeyEvent.ACTION_DOWN) return@onKeyEvent true
                    onInteraction()
                    val step = if (forward) forwardStepMs else backStepMs
                    val time = native.eventTime
                    val delta =
                        if (native.repeatCount == 0) {
                            holdStart = time
                            step
                        } else {
                            TvPlayerKeyHandler.holdSeekMs(
                                step,
                                durationMs,
                                heldMs = time - holdStart,
                                sinceLastMs = time - lastRepeat,
                            )
                        }
                    lastRepeat = time
                    val from = pendingMs ?: positionMs
                    val target = from + if (forward) delta else -delta
                    onPendingChange(
                        if (durationMs > 0) target.coerceIn(0L, durationMs)
                        else target.coerceAtLeast(0L)
                    )
                    true
                }
                .focusable(interactionSource = interactionSource)
    ) {
        val y = size.height / 2
        val stroke = size.height / 2
        drawLine(
            color = color.copy(alpha = 0.24f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width * fraction, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        chapterMarkers.forEach { marker ->
            if (marker <= 0f || marker >= 1f) return@forEach
            drawLine(
                color = Color.White,
                start = Offset(size.width * marker, y - stroke / 2),
                end = Offset(size.width * marker, y + stroke / 2),
                strokeWidth = (size.height / 4).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
        if (isFocused) {
            val thumb = Offset(size.width * fraction, y)
            drawIntoCanvas { it.drawCircle(thumb, size.height / 2, haloPaint) }
            drawCircle(color = color, radius = size.height / 2, center = thumb)
        }
    }
}
