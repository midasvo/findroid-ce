package dev.jdtech.jellyfin.ce.thememusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * CE (#141): plays [url], the show's theme song, while this page is shown (see [ThemeMusicSession]
 * for the rules). Null means no song, or the setting is off. Call [ThemeMusicSession.onPlaybackStarted]
 * on the returned session right before going to the player.
 */
@Composable
fun rememberThemeMusic(url: String?): ThemeMusicSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Kept with the page in the back stack, so Back from a season doesn't replay the song.
    var played by rememberSaveable { mutableStateOf(false) }

    val session = remember {
            val audioManager = context.getSystemService(AudioManager::class.java)
            lateinit var session: ThemeMusicSession
            val player = ExoThemeMusicPlayer(context) { session.onFinished() }
            session =
                ThemeMusicSession(
                    output = player,
                    isOtherAudioActive = { audioManager?.isMusicActive == true },
                    played = played,
                    onPlayed = { played = true },
                )
            session
        }

    LaunchedEffect(url) { session.onSongFound(url) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session.onForeground()
                Lifecycle.Event.ON_STOP -> session.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        // The screensaver (ours from #140 or the system's) normally stops the activity too, but
        // not on every device, so listen for it as well.
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    session.onScreensaverStarted()
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_DREAMING_STARTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            // Fades out on its own handler, then releases the player.
            session.onLeave()
        }
    }

    return session
}
