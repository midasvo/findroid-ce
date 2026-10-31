package dev.jdtech.jellyfin.player.local

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaSession?.let {
            if (!sessions.contains(it)) {
                addSession(it)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mediaSession?.let {
            removeSession(it)
        }
        super.onDestroy()
    }

    companion object {
        var mediaSession: MediaSession? = null
    }
}
