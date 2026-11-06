package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.ephyra.drawSpecks
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * Stands in for a screen's content when there is nothing to show, with room for [actions].
 *
 * [specks] draws the speck field behind the icon; off for errors, which aren't empty states
 * (docs/identity/VISION.md §9).
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    specks: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // A still speck field (VISION §9) in a 240 × 160 dp area centred on the icon, overflowing
            // it without taking space; half as bright in light theme.
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val alphaScale = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.5f else 1f
            if (specks) {
                Canvas(modifier = Modifier.matchParentSize().requiredSize(240.dp, 160.dp)) {
                    drawSpecks(
                        count = 24,
                        seed = 42,
                        primary = primary,
                        tertiary = tertiary,
                        alphaScale = alphaScale,
                    )
                }
            }
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(24.dp))
            actions()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    FindroidTheme { EmptyState(icon = CoreR.drawable.ic_heart, title = "You have no favorites") }
}
