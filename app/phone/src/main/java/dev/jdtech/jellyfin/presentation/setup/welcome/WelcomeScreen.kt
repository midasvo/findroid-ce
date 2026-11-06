package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.components.SwimmingJellyfish
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeAction

@Composable
fun WelcomeScreen(onContinueClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    WelcomeScreenLayout(
        onAction = { action ->
            when (action) {
                is WelcomeAction.OnContinueClick -> onContinueClick()
                is WelcomeAction.OnLearnMoreClick -> {
                    uriHandler.openUri("https://jellyfin.org/")
                }
            }
        }
    )
}

@Composable
private fun WelcomeScreenLayout(onAction: (WelcomeAction) -> Unit) {
    RootLayout(padding = PaddingValues(horizontal = 24.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
        ) {
            // CE (#211): the logo swims here too; it's the same jellyfish as on the About screen.
            // CE (#220): a still primary halo behind it, 1.6 × the mark across, fainter in light.
            val halo =
                MaterialTheme.colorScheme.primary.copy(
                    alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.10f else 0.22f
                )
            SwimmingJellyfish(
                logoSize = 120.dp,
                modifier =
                    Modifier.drawBehind {
                        val radius = 0.8f * 120.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(halo, halo.copy(alpha = 0f)), radius = radius),
                            radius = radius,
                        )
                    },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(SetupR.string.ce_welcome),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(SetupR.string.ce_welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                OutlinedButton(
                    onClick = { onAction(WelcomeAction.OnLearnMoreClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(SetupR.string.welcome_btn_learn_more))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onAction(WelcomeAction.OnContinueClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(SetupR.string.welcome_btn_continue))
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun WelcomeScreenLayoutPreview() {
    FindroidTheme { WelcomeScreenLayout(onAction = {}) }
}
