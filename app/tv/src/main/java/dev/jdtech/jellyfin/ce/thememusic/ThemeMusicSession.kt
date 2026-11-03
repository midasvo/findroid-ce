package dev.jdtech.jellyfin.ce.thememusic

/** What [ThemeMusicSession] drives. The real one is [ExoThemeMusicPlayer]. */
interface ThemeMusicOutput {
    /** Start [url] from silence and fade in. */
    fun play(url: String)

    /** Fade out and release. */
    fun fadeOut()
}

/**
 * CE (#141): when the TV show page plays its theme song. Pure, so the rules are unit-tested.
 *
 * The song plays once per visit to the page. It starts as soon as there is a song (the show
 * ViewModel only looks one up when the setting is on) and the app is in the foreground, unless
 * another app is already playing audio: we don't take audio focus, so we'd play over it.
 *
 * It stops, with a fade, on leaving the page, playback starting, the app going to the background
 * and the screensaver. Once it has started it doesn't start again during this visit, so Back from
 * a season or the player stays quiet; [played] is kept with the page for that (see
 * [rememberThemeMusic]).
 */
class ThemeMusicSession(
    private val output: ThemeMusicOutput,
    private val isOtherAudioActive: () -> Boolean,
    played: Boolean = false,
    private val onPlayed: () -> Unit = {},
) {
    /** True once the song started, or was skipped for good, during this visit. */
    var played: Boolean = played
        private set

    var isPlaying: Boolean = false
        private set

    private var url: String? = null
    private var foreground = false

    fun onSongFound(url: String?) {
        this.url = url
        maybeStart()
    }

    fun onForeground() {
        foreground = true
        maybeStart()
    }

    fun onBackground() {
        foreground = false
        stop()
    }

    /** Also before the song started: it shouldn't greet you on the way back from the player. */
    fun onPlaybackStarted() {
        markPlayed()
        stop()
    }

    fun onScreensaverStarted() = stop()

    fun onLeave() = stop()

    /** The song played to its end, or failed to load. */
    fun onFinished() {
        isPlaying = false
    }

    private fun maybeStart() {
        val url = url ?: return
        if (played || !foreground) return
        // Checked once, at the moment it would start. Music that starts halfway through the visit,
        // after the other app went quiet, would only be a surprise.
        markPlayed()
        if (isOtherAudioActive()) return
        isPlaying = true
        output.play(url)
    }

    private fun stop() {
        if (!isPlaying) return
        isPlaying = false
        output.fadeOut()
    }

    private fun markPlayed() {
        if (played) return
        played = true
        onPlayed()
    }
}
