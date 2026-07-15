package dev.jdtech.jellyfin

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel

abstract class BasePlayerActivity : AppCompatActivity() {

    abstract val viewModel: PlayerViewModel

    private var wasPip: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()

        if (wasPip) {
            wasPip = false
        } else {
            viewModel.player.playWhenReady = viewModel.playWhenReady
        }
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()

        if (isInPictureInPictureMode) {
            wasPip = true
        } else {
            viewModel.playWhenReady = viewModel.player.playWhenReady
            if (!viewModel.isBackgroundAudioEnabled) {
                viewModel.player.playWhenReady = false
            }
            viewModel.updatePlaybackProgress()
        }
    }

    override fun onStop() {
        super.onStop()

        if (wasPip) {
            finish()
        }
    }

    protected fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    protected fun configureInsets(playerControls: View) {
        playerControls.setOnApplyWindowInsetsListener { _, windowInsets ->
            val cutout = windowInsets.displayCutout
            playerControls.updatePadding(
                left = cutout?.safeInsetLeft ?: 0,
                top = cutout?.safeInsetTop ?: 0,
                right = cutout?.safeInsetRight ?: 0,
                bottom = cutout?.safeInsetBottom ?: 0,
            )
            return@setOnApplyWindowInsetsListener windowInsets
        }
    }
}
