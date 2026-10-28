package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/** What the trailing control of a download row shows. */
enum class DownloadControlState {
    WAITING,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    FAILED,
    COMPLETED,
}

private enum class ControlKind {
    RING,
    RETRY,
    DONE,
}

/**
 * App Store-style download control: a ring that fills with progress around a stop glyph, so
 * the progress indicator and the cancel button are one element. A failed download shows a
 * retry glyph instead and a finished one a check, each popping in with a small spring.
 */
@Composable
fun DownloadProgressButton(
    state: DownloadControlState,
    progress: () -> Float,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val kind =
        when (state) {
            DownloadControlState.FAILED -> ControlKind.RETRY
            DownloadControlState.COMPLETED -> ControlKind.DONE
            else -> ControlKind.RING
        }
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier
                )
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription?.let { this.stateDescription = it }
                },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                ) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "downloadControl",
        ) { target ->
            when (target) {
                ControlKind.RING -> ProgressRing(state = state, progress = progress)
                ControlKind.RETRY ->
                    Glyph(
                        icon = CoreR.drawable.ic_rotate_ccw,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                ControlKind.DONE ->
                    Glyph(
                        icon = CoreR.drawable.ic_check,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
            }
        }
    }
}

@Composable
private fun ProgressRing(state: DownloadControlState, progress: () -> Float) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor =
        when (state) {
            DownloadControlState.WAITING,
            DownloadControlState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        }
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (state == DownloadControlState.PREPARING) {
            CircularProgressIndicator(
                modifier = Modifier.matchParentSize(),
                color = activeColor,
                strokeWidth = 2.5.dp,
                trackColor = trackColor,
            )
        } else {
            CircularProgressIndicator(
                progress = if (state == DownloadControlState.WAITING) ({ 0f }) else progress,
                modifier = Modifier.matchParentSize(),
                color = activeColor,
                strokeWidth = 2.5.dp,
                trackColor = trackColor,
                gapSize = 0.dp,
            )
        }
        // Stop glyph: tapping the ring cancels.
        Box(
            modifier =
                Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(activeColor)
        )
    }
}

@Composable
private fun Glyph(icon: Int, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadProgressButtonPreview() {
    FindroidTheme {
        Row {
            DownloadControlState.entries.forEach { state ->
                DownloadProgressButton(
                    state = state,
                    progress = { 0.6f },
                    contentDescription = state.name,
                    onClick = {},
                )
            }
        }
    }
}
