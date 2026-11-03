package dev.jdtech.jellyfin.ce.presentation.screensaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject

/**
 * CE (#140): System screensaver settings activity for Findroid CE.
 */
@AndroidEntryPoint
class ScreensaverSettingsActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FindroidTheme {
                ScreensaverSettingsScreen(
                    appPreferences = appPreferences,
                    onBack = { finish() },
                )
            }
        }
    }
}
