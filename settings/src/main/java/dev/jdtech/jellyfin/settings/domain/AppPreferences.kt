package dev.jdtech.jellyfin.settings.domain

import android.content.SharedPreferences
import dev.jdtech.jellyfin.settings.domain.models.Preference
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class AppPreferences @Inject constructor(val sharedPreferences: SharedPreferences) {
    // Server
    val currentServer = Preference<String?>("pref_current_server", null)
    // CE (#250): the session the API client starts with, a copy of what the database says for
    // [currentServer], so the client is built without waiting for Room. Not a user setting.
    val sessionServerId = Preference<String?>("pref_session_server_id", null)
    val sessionBaseUrl = Preference<String?>("pref_session_base_url", null)
    val sessionAccessToken = Preference<String?>("pref_session_access_token", null)
    val sessionUserId = Preference<String?>("pref_session_user_id", null)

    // Language
    val preferredAudioLanguage = Preference<String?>("pref_audio_language", null)
    val preferredSubtitleLanguage = Preference<String?>("pref_subtitle_language", null)

    // Interface
    val theme = Preference("pref_theme", "system")
    val dynamicColors = Preference("pref_dynamic_colors", false)
    val homeSuggestions = Preference<Boolean>("home_suggestions", true)
    val homeContinueWatching = Preference<Boolean>("home_continue_watching", true)
    val homeNextUp = Preference<Boolean>("home_next_up", true)
    // 0 is no limit. The web client's default is 365.
    val homeNextUpMaxDays = Preference("pref_home_next_up_max_days", 0)
    val homeLatest = Preference<Boolean>("home_latest", true)
    // CE (#138): the Android TV launcher's Watch Next row. Only the TV app shows and uses it.
    val tvWatchNext = Preference("pref_tv_watch_next", true)
    // CE (#140): Android TV screensaver.
    val tvScreensaverSource = Preference("pref_tv_screensaver_source", "random")
    val tvScreensaverInterval = Preference("pref_tv_screensaver_interval", "20")
    val tvScreensaverShowClock = Preference("pref_tv_screensaver_show_clock", true)
    // CE (#141): play a series' theme song on the TV show page. Only the TV app shows and uses it.
    val tvThemeMusic = Preference("pref_tv_theme_music", false)
    val displayExtraInfo = Preference("pref_display_extra_info", false)
    val displayCastAndCrew = Preference("pref_display_cast_and_crew", true)
    // CE (#188): show the Favorites tab in the bottom navigation bar.
    val showFavoritesTab = Preference("pref_show_favorites_tab", true)
    // CE (#245): open on the user list when the server has more than one saved user.
    val askUserOnStart = Preference("pref_ask_user_on_start", false)

    // Player
    val playerBackend = Preference("pref_player_backend", "exoplayer")
    val playerBrightness = Preference("pref_player_brightness", -1.0f)
    val playerBackgroundAudio = Preference("pref_player_background_audio", false)

    // Maximum streaming bitrate in bits per second; the server transcodes
    // anything above it. 0 means no cap (original quality). Set from the
    // player's quality selector.
    val playerMaxBitrate = Preference("pref_player_max_bitrate", 0)

    // Platform audio decoders that failed during playback on this device, see
    // AudioDecoderFallback. Not a user setting.
    val playerBrokenAudioDecoders =
        Preference("pref_player_broken_audio_decoders", emptySet<String>())

    // ExoPlayer direct-plays the Dolby Vision profiles the device's decoders and
    // display advertise. They can advertise DV and still give a bad picture, so
    // this can hand DV back to the server. mpv always direct-plays.
    val playerDirectPlayDolbyVision = Preference("pref_player_direct_play_dovi", true)

    // Player - mpv
    val playerMpv = Preference("pref_player_mpv", false)
    val playerMpvHwdec = Preference("pref_player_mpv_hwdec", "mediacodec")
    val playerMpvVo = Preference("pref_player_mpv_vo", "gpu-next")

    // CE divergence from upstream (issue #52 / upstream #1246): upstream defaults this to
    // "aaudio", which freezes the video pipeline after any flush (seek, audio-track switch,
    // next episode, surface recreation) while audio keeps playing. Every confirmed report so
    // far is a Samsung device, but upstream's own bisect concluded the fault is with the
    // aaudio output itself and reproduces it in mpv-android too, so do not narrow this to a
    // manufacturer check — see mpv-android/mpv-android#1283, still unfixed.
    // "audiotrack" is also what mpv's own autoprobe order picks on Android, and what
    // mpv-android uses. aaudio stays selectable for anyone who wants it.
    val playerMpvAo =
        Preference("pref_player_mpv_ao", Constants.MpvAudioOutput.AUDIOTRACK)

    // Player - gestures
    val playerGestures = Preference("pref_player_gestures", true)
    val playerGesturesVB = Preference("pref_player_gestures_vb", true)
    val playerGesturesZoom = Preference("pref_player_gestures_zoom", true)
    val playerGesturesSeek = Preference("pref_player_gestures_seek", true)
    val playerGesturesSeekTrickplay = Preference("pref_player_gestures_seek_trickplay", true)
    val playerGesturesHold = Preference("pref_player_gestures_hold", "chapter")
    val playerGesturesBrightnessRemember = Preference("pref_player_brightness_remember", false)
    val playerGesturesStartMaximized = Preference("pref_player_start_maximized", false)

    // Player - seeking
    val playerSeekBackInc = Preference("pref_player_seek_back_inc", 5_000L)
    val playerSeekForwardInc = Preference("pref_player_seek_forward_inc", 15_000L)
    val playerChapterMarkers = Preference("pref_player_chapter_markers", true)

    // CE (#126): TV remote. The steps are strings because TV settings only have selects.
    val playerTvSeekBackInc = Preference("pref_player_tv_seek_back_inc", "10000")
    val playerTvSeekForwardInc = Preference("pref_player_tv_seek_forward_inc", "30000")
    val playerTvOkPauses = Preference("pref_player_tv_ok_pauses", false)
    val playerTvMatchRefreshRate = Preference("pref_player_tv_match_refresh_rate", false)

    // Player - Media Segments
    //
    // CE (#271): the old global switches and type lists below are no longer in the settings UI.
    // migrateMediaSegmentActions reads them once to fill in the per-type actions.
    val playerMediaSegmentsSkipButton
        get() = Preference("pref_player_media_segments_skip_button", true)

    val playerMediaSegmentsSkipButtonType
        get() = Preference("pref_player_media_segments_skip_button_type", setOf("INTRO", "OUTRO"))

    val playerMediaSegmentsAutoSkip
        get() = Preference("pref_player_media_segments_auto_skip", false)

    val playerMediaSegmentsAutoSkipType
        get() = Preference("pref_player_media_segments_auto_skip_type", setOf("INTRO", "OUTRO"))

    /** How long the skip button shows after entering a segment, in seconds. Phone and TV. */
    val playerMediaSegmentsSkipButtonDuration
        get() = Preference("pref_player_media_segments_skip_button_duration", 5L)

    /** Auto-skip always, or only in picture-in-picture (phone). */
    val playerMediaSegmentsAutoSkipMode
        get() =
            Preference(
                "pref_player_media_segments_auto_skip_mode",
                Constants.PlayerMediaSegmentsAutoSkip.ALWAYS,
            )

    val playerMediaSegmentsNextEpisodeThreshold
        get() = Preference("pref_player_media_segments_next_episode_threshold", 5_000L)

    // Player - "Are you still watching?" inactivity prompt.
    // 0 disables the corresponding axis. Either axis tripping fires the prompt.
    val stillWatchingAfterEpisodes = Preference("pref_player_still_watching_after_episodes", 3)
    val stillWatchingAfterMinutes = Preference("pref_player_still_watching_after_minutes", 90)
    val stillWatchingPromptTimeoutSeconds =
        Preference("pref_player_still_watching_prompt_timeout_seconds", 30)

    // Player - "Play next episode automatically". "server" follows the Jellyfin user's
    // enableNextEpisodeAutoPlay; "on" / "off" override it (Constants.PlayerAutoPlayNext).
    val playerAutoPlayNext =
        Preference<String?>("pref_player_autoplay_next", Constants.PlayerAutoPlayNext.SERVER)

    // Player - Media Segments per-type action (MediaSegmentsMigration.SKIP / ASK / IGNORE).
    // CE (#271): always set. Existing installs get their values from the old switches through
    // migrateMediaSegmentActions; the defaults are what a fresh install had before.
    val playerMediaSegmentsIntroAction
        get() = Preference("pref_player_media_segments_intro_action", MediaSegmentsMigration.ASK)

    val playerMediaSegmentsOutroAction
        get() = Preference("pref_player_media_segments_outro_action", MediaSegmentsMigration.ASK)

    val playerMediaSegmentsRecapAction
        get() =
            Preference("pref_player_media_segments_recap_action", MediaSegmentsMigration.IGNORE)

    val playerMediaSegmentsPreviewAction
        get() =
            Preference("pref_player_media_segments_preview_action", MediaSegmentsMigration.IGNORE)

    val playerMediaSegmentsCommercialAction
        get() =
            Preference(
                "pref_player_media_segments_commercial_action",
                MediaSegmentsMigration.IGNORE,
            )

    /** The per-type action preferences, keyed by segment type name (`INTRO`, `OUTRO`, ...). */
    val playerMediaSegmentsActions: Map<String, Preference<String>>
        get() =
            mapOf(
                MediaSegmentsMigration.INTRO to playerMediaSegmentsIntroAction,
                MediaSegmentsMigration.OUTRO to playerMediaSegmentsOutroAction,
                MediaSegmentsMigration.RECAP to playerMediaSegmentsRecapAction,
                MediaSegmentsMigration.PREVIEW to playerMediaSegmentsPreviewAction,
                MediaSegmentsMigration.COMMERCIAL to playerMediaSegmentsCommercialAction,
            )

    // Player - trickplay
    val playerTrickplay = Preference("pref_player_trickplay", true)

    // Player - PiP
    val playerPipGesture = Preference("pref_player_picture_in_picture_gesture", false)

    // Player - Subtitle styling
    // Colors are stored as 8-digit ARGB hex strings (e.g. "#FFFFFFFF") so we can keep the
    // existing primitive-only DataStore. Edge type / font family are short keys mapped to
    // CaptionStyleCompat constants and Android Typefaces respectively by the player wiring.
    val subtitleForegroundColor =
        Preference<String?>(
            "pref_subtitle_foreground_color",
            Constants.SubtitleStyle.DEFAULT_FG_COLOR,
        )
    val subtitleBackgroundColor =
        Preference<String?>(
            "pref_subtitle_background_color",
            Constants.SubtitleStyle.DEFAULT_BG_COLOR,
        )
    val subtitleEdgeColor =
        Preference<String?>(
            "pref_subtitle_edge_color",
            Constants.SubtitleStyle.DEFAULT_EDGE_COLOR,
        )
    val subtitleEdgeType =
        Preference<String?>("pref_subtitle_edge_type", Constants.SubtitleStyle.EDGE_OUTLINE)
    val subtitleFontFamily =
        Preference<String?>("pref_subtitle_font_family", Constants.SubtitleStyle.FONT_DEFAULT)
    val subtitleFontScale = Preference("pref_subtitle_font_scale", 100)

    // Downloads
    val downloadOverMobileData = Preference("pref_downloads_mobile_data", false)
    val downloadWhenRoaming = Preference("pref_downloads_roaming", false)
    val downloadStorageIndex = Preference<String?>("pref_downloads_storage_index", null)
    val maxConcurrentDownloads = Preference("pref_downloads_max_concurrent", 2)
    val smartDownloads = Preference("pref_downloads_smart", false)
    val smartDownloadsKeepReady = Preference("pref_downloads_smart_keep", 3)

    // Epoch ms at which Smart Downloads was turned on. It only removes downloads that were
    // watched after this moment; 0 means not recorded yet.
    val smartDownloadsSince = Preference("pref_downloads_smart_since", 0L)

    // When enabled, Dolby Vision files are downloaded as a device-compatible
    // (H.264) transcode so they play offline. Non-DV files stay original.
    val downloadTranscodeDolbyVision = Preference("pref_downloads_transcode_dovi", true)

    // Network
    val requestTimeout =
        Preference("pref_network_request_timeout", Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT)
    val connectTimeout =
        Preference("pref_network_connect_timeout", Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT)
    val socketTimeout =
        Preference("pref_network_socket_timeout", Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT)

    // Library filter
    val filterWatched = Preference("pref_filter_watched", false)

    // Cache
    val imageCache = Preference("pref_image_cache", true)
    val imageCacheSize = Preference("pref_image_cache_size", 20)
    val imageDisplaySize = Preference("pref_image_display_size", false)
    // CE (#251): Home shows the rows of the last visit while the server answers.
    val homeCache = Preference("pref_home_cache", true)

    // Sorting
    val sortBy = Preference("pref_sort_by", "SortName")
    val sortOrder = Preference("pref_sort_order", "Ascending")

    // Offline mode
    val offlineMode = Preference("pref_offline_mode", false)

    // Permissions
    val localNetworkPermissionDecided = Preference("pref_permission_local_network_decided", false)

    // Migrations
    val mpvMigrated = Preference("mpv_migrated", false)

    val mpvAoAaudioMigrated = Preference("pref_player_mpv_ao_aaudio_migrated", false)

    val mediaSegmentActionsMigrated =
        Preference("pref_player_media_segments_actions_migrated", false)

    /**
     * CE (#271): one-shot move from the old global media segment switches (show skip button,
     * auto skip, and their type lists) to one stored action per segment type, so the settings
     * show every type's effective value. An action the user already picked per type is kept. See
     * [MediaSegmentsMigration.actions] for the mapping.
     */
    fun migrateMediaSegmentActions() {
        if (getValue(mediaSegmentActionsMigrated)) return
        val preferences = playerMediaSegmentsActions
        val explicit =
            preferences.mapValues { (_, preference) ->
                // Read without the default: an absent value is "never picked".
                try {
                    sharedPreferences.getString(preference.backendName, null)
                } catch (_: ClassCastException) {
                    null
                }
            }
        val actions =
            MediaSegmentsMigration.actions(
                explicit = explicit,
                autoSkip = getValue(playerMediaSegmentsAutoSkip),
                autoSkipTypes = getValue(playerMediaSegmentsAutoSkipType),
                skipButton = getValue(playerMediaSegmentsSkipButton),
                skipButtonTypes = getValue(playerMediaSegmentsSkipButtonType),
            )
        Timber.i("Migrating media segment settings to per-type actions: %s", actions)
        preferences.forEach { (type, preference) -> setValue(preference, actions.getValue(type)) }
        setValue(mediaSegmentActionsMigrated, true)
    }

    /**
     * One-shot move off the broken "aaudio" audio output (issue #52).
     *
     * Flipping [playerMpvAo]'s default is not enough on its own: the settings picker persists a
     * value on *any* tap, including on the row that is already selected, so everyone who merely
     * opened Audio output while troubleshooting the freeze has "aaudio" written to disk — which
     * is exactly the population that hit the bug. Rewrite that one value once.
     *
     * Guarded by [mpvAoAaudioMigrated] so a deliberate re-selection afterwards sticks; we correct
     * an accidental value, we don't keep overruling the user.
     */
    fun migrateMpvAudioOutput() {
        if (getValue(mpvAoAaudioMigrated)) return
        if (getValue(playerMpvAo) == Constants.MpvAudioOutput.AAUDIO) {
            Timber.i("Migrating mpv audio output off aaudio")
            setValue(playerMpvAo, Constants.MpvAudioOutput.AUDIOTRACK)
        }
        setValue(mpvAoAaudioMigrated, true)
    }

    inline fun <reified T> getValue(preference: Preference<T>): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            when (preference.defaultValue) {
                is Boolean ->
                    sharedPreferences.getBoolean(preference.backendName, preference.defaultValue)
                        as T
                is Int ->
                    sharedPreferences.getInt(preference.backendName, preference.defaultValue) as T
                is Long ->
                    sharedPreferences.getLong(preference.backendName, preference.defaultValue) as T
                is Float ->
                    sharedPreferences.getFloat(preference.backendName, preference.defaultValue) as T
                is String? ->
                    sharedPreferences.getString(preference.backendName, preference.defaultValue)
                        as T
                is Set<*> ->
                    sharedPreferences.getStringSet(
                        preference.backendName,
                        preference.defaultValue as Set<String>,
                    ) as T
                else -> preference.defaultValue
            }
        } catch (_: Exception) {
            Timber.w(
                "Failed to load ${preference.backendName} preference. Resetting to default value..."
            )
            setValue(preference, preference.defaultValue)
            preference.defaultValue
        }
    }

    /** Emits the current value of [preference] and then emits a new value every time it changes. */
    inline fun <reified T> observe(preference: Preference<T>): Flow<T> = callbackFlow {
        trySend(getValue(preference))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == preference.backendName) {
                trySend(getValue(preference))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    inline fun <reified T> setValue(preference: Preference<T>, value: T) {
        val editor = sharedPreferences.edit()
        @Suppress("UNCHECKED_CAST")
        when (preference.defaultValue) {
            is Boolean -> editor.putBoolean(preference.backendName, value as Boolean)
            is Int -> editor.putInt(preference.backendName, value as Int)
            is Long -> editor.putLong(preference.backendName, value as Long)
            is Float -> editor.putFloat(preference.backendName, value as Float)
            is String? -> editor.putString(preference.backendName, value as String?)
            is Set<*> -> editor.putStringSet(preference.backendName, value as Set<String>)
            else -> throw IllegalArgumentException("Unsupported preference type: ${preference.defaultValue}")
        }
        editor.apply()
    }
}
