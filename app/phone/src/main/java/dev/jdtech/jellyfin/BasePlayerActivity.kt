package dev.jdtech.jellyfin

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import dev.jdtech.jellyfin.player.local.PlaybackSessionStore
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel

abstract class BasePlayerActivity : AppCompatActivity() {

    abstract val viewModel: PlayerViewModel

    /** Owns [viewModel], and with it the player, so a session can outlive this activity. */
    abstract val sessionStore: PlaybackSessionStore

    private var wasPip: Boolean = false

    /** The user closed the player, as opposed to the activity being removed from under it. */
    private var closedByUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sessionStore.onUiAttached()
    }

    override fun finish() {
        // Every close from the app (back, the close button, the end of the queue) comes through
        // here. The system removing the task while audio plays in the background does not.
        closedByUser = true
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChangingConfigurations || !sessionStore.hasSession) return

        // With background audio on and playing, the session carries on in PlaybackService
        // without this activity. Anything else ends it, as closing the player always has.
        if (!closedByUser && viewModel.isBackgroundAudioEnabled && viewModel.player.isPlaying) {
            sessionStore.onUiDetached(isPlaying = true)
        } else {
            sessionStore.clear()
        }
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
            if (viewModel.isBackgroundAudioEnabled && viewModel.player.isPlaying) {
                // PiP was closed while playing: carry on as background audio. The media
                // notification brings the player back.
                wasPip = false
            } else {
                finish()
            }
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
