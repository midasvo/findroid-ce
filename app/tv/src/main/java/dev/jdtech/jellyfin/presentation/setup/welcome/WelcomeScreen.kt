package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
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
    val focusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            // CE (#220): the mark at 200 dp on a primary halo 1.6 × its size across.
            val halo = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            Icon(
                painter = painterResource(id = R.drawable.ic_banner),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier =
                    Modifier.height(200.dp).drawBehind {
                        val radius = 0.8f * 200.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(halo, halo.copy(alpha = 0f)), radius = radius),
                            radius = radius,
                        )
                    },
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            Text(
                text = stringResource(dev.jdtech.jellyfin.setup.R.string.ce_welcome),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            Text(
                text = stringResource(dev.jdtech.jellyfin.setup.R.string.ce_welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
            OutlinedButton(onClick = { onAction(WelcomeAction.OnLearnMoreClick) }) {
                Text(
                    text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome_btn_learn_more)
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            Button(
                onClick = { onAction(WelcomeAction.OnContinueClick) },
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome_btn_continue))
            }
        }
    }

    LaunchedEffect(true) { focusRequester.requestFocus() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun WelcomeScreenLayoutPreview() {
    FindroidTheme { WelcomeScreenLayout(onAction = {}) }
}
