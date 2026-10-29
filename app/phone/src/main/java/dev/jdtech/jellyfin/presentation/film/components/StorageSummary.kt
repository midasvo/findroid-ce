package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private class StorageSegment(val label: String, val bytes: Long, val color: Color)

/**
 * Storage card for the top of the Downloads screen: one segmented bar (movies, shows,
 * everything else, free space) and a single line saying how much downloads take.
 */
@Composable
fun StorageSummary(
    moviesBytes: Long,
    showsBytes: Long,
    usedBytes: Long,
    freeBytes: Long,
    totalBytes: Long,
    isExternal: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Running low: turn the free space red so it is noticed before a download fails.
    val lowOnSpace = totalBytes > 0 && freeBytes < totalBytes / 10
    val segments =
        listOf(
            StorageSegment(
                stringResource(CoreR.string.movies_label),
                moviesBytes,
                MaterialTheme.colorScheme.primary,
            ),
            StorageSegment(
                stringResource(CoreR.string.shows_label),
                showsBytes,
                MaterialTheme.colorScheme.tertiary,
            ),
            StorageSegment(
                stringResource(CoreR.string.storage_legend_other),
                (totalBytes - freeBytes - moviesBytes - showsBytes).coerceAtLeast(0L),
                MaterialTheme.colorScheme.outline,
            ),
        )

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (totalBytes > 0) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clearAndSetSemantics {}
                ) {
                    segments.forEach { segment ->
                        if (segment.bytes > 0) {
                            // Keep tiny segments visible as at least a sliver.
                            val fraction =
                                (segment.bytes.toFloat() / totalBytes).coerceIn(0.01f, 1f)
                            Box(
                                modifier =
                                    Modifier.fillMaxHeight()
                                        .weight(fraction)
                                        .background(segment.color)
                            )
                        }
                    }
                    val freeFraction = freeBytes.toFloat() / totalBytes
                    if (freeFraction > 0f) Spacer(Modifier.weight(freeFraction))
                }
                Spacer(Modifier.height(12.dp))
            }
            val summary =
                stringResource(
                    CoreR.string.downloads_storage_summary,
                    Formatter.formatShortFileSize(context, usedBytes),
                    Formatter.formatShortFileSize(context, freeBytes),
                )
            Text(
                text =
                    if (isExternal) "$summary · ${stringResource(CoreR.string.storage_sd_card)}"
                    else summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color =
                    if (lowOnSpace) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
            if (totalBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    segments
                        .filter { it.bytes > 0 }
                        .forEach { LegendEntry(label = it.label, color = it.color) }
                    LegendEntry(
                        label = stringResource(CoreR.string.storage_legend_free),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendEntry(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StorageSummaryPreview() {
    FindroidTheme {
        StorageSummary(
            moviesBytes = 4_000_000_000L,
            showsBytes = 9_000_000_000L,
            usedBytes = 13_200_000_000L,
            freeBytes = 38_000_000_000L,
            totalBytes = 128_000_000_000L,
            isExternal = false,
        )
    }
}
