package dev.jdtech.jellyfin.player.local

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Owns the [dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel], and with it the player
 * and its media session, for the phone player.
 *
 * The view model lives here instead of in the player activity, so playback can outlive the
 * activity: with background audio on, the system or the user can remove the activity while the
 * [PlaybackService] keeps the session, the notification and the progress reports going. The
 * activity attaches to the same view model again when the notification brings it back.
 *
 * The session is released by [clear], which clears the view model: when the user closes the
 * player, when the activity goes away without anything left to play in the background, or when a
 * session has been paused with no player screen for [IDLE_RELEASE_MS].
 */
@Singleton
class PlaybackSessionStore @Inject constructor() : ViewModelStoreOwner {

    override var viewModelStore: ViewModelStore = ViewModelStore()
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable {
        Timber.d("Releasing the idle playback session")
        clear()
    }

    /** Whether a player view model exists, i.e. there is a session to come back to. */
    var hasSession: Boolean = false
        private set

    /** Whether the player activity is currently attached to the session. */
    var isUiAttached: Boolean = false
        private set

    fun onSessionCreated() {
        hasSession = true
    }

    fun onUiAttached() {
        isUiAttached = true
        handler.removeCallbacks(idleRelease)
    }

    /** The player activity went away, without the session being released. */
    fun onUiDetached(isPlaying: Boolean) {
        isUiAttached = false
        onPlayingChanged(isPlaying)
    }

    /** Called when playback starts or stops, to time out a paused session nobody is looking at. */
    fun onPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(idleRelease)
        if (hasSession && !isUiAttached && !isPlaying) {
            handler.postDelayed(idleRelease, IDLE_RELEASE_MS)
        }
    }

    /** Releases the session: clears the view model, which releases the player and the service. */
    fun clear() {
        handler.removeCallbacks(idleRelease)
        isUiAttached = false
        val store = viewModelStore
        viewModelStore = ViewModelStore()
        hasSession = false
        store.clear()
    }

    private companion object {
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L
    }
}
