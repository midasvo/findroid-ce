package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.window.layout.WindowMetricsCalculator
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.player.local.PlaybackSessionStore
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.ChapterListDialogFragment
import dev.jdtech.jellyfin.presentation.player.QualitySelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.SpeedSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.StillWatchingDialogFragment
import dev.jdtech.jellyfin.presentation.player.TrackSelectionDialogFragment
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import dev.jdtech.jellyfin.utils.applySubtitleStyle
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

var isControlsLocked: Boolean = false

/** Below this many dp a window is compact width (Material window size classes). */
private const val COMPACT_WIDTH_DP = 600

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    @Inject override lateinit var sessionStore: PlaybackSessionStore
    override val viewModel: PlayerViewModel by lazy {
        ViewModelProvider(
            sessionStore.viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras,
        )[PlayerViewModel::class.java]
    }
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false

    private lateinit var skipSegmentButton: Button

    private var stillWatchingDialog: StillWatchingDialogFragment? = null

    /**
     * CE (R6, T7): the orientation the player asks for while the controls are not locked. Phones
     * keep the forced landscape; tablets and unfolded foldables play in whatever orientation they
     * are held. Set by [updatePlayerOrientation].
     */
    private var playerOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    /**
     * Picks [playerOrientation] from the window the player gets when it fills the screen: forced
     * landscape when that window is compact width (< 600 dp) held in portrait, i.e. its shorter
     * side is under 600 dp; otherwise unspecified.
     *
     * The shorter side and not the current width, because a phone held in landscape is wider than
     * 600 dp and must still lock. The maximum window and not the current one, because a requested
     * orientation only applies to a full-screen window: a tablet that starts the player in
     * split-screen, or returns from picture-in-picture, must not lock itself to landscape. Folding
     * or unfolding changes the maximum window, hence the call from [onConfigurationChanged].
     */
    private fun updatePlayerOrientation() {
        val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).bounds
        val shorterSideDp = minOf(bounds.width(), bounds.height()) / resources.displayMetrics.density
        playerOrientation =
            if (shorterSideDp < COMPACT_WIDTH_DP) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (!isControlsLocked && requestedOrientation != playerOrientation) {
            requestedOrientation = playerOrientation
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlayerOrientation()
    }

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * CE (#271): the skip button shows in its first seconds in a segment (the ViewModel's
     * [PlayerViewModel.UiState.skipButtonInWindow]) and, for the rest of the segment, whenever the
     * controls are visible. Never in picture-in-picture.
     */
    private fun updateSkipSegmentButton(uiState: PlayerViewModel.UiState) {
        val segment = uiState.currentSegment
        skipSegmentButton.isVisible =
            segment != null &&
                !isInPictureInPictureMode &&
                (uiState.skipButtonInWindow || binding.playerView.isControllerFullyVisible)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without an item the media notification brought us back to a session that is still
        // playing in the background. If it is gone by now there is nothing to show.
        val itemIdExtra = intent.extras?.getString("itemId")
        if (itemIdExtra == null && !sessionStore.hasSession) {
            finish()
            return
        }
        val itemId = itemIdExtra?.let { UUID.fromString(it) }
        val itemKind = intent.extras?.getString("itemKind")
        val startFromBeginning = intent.extras?.getBoolean("startFromBeginning") ?: false
        val mediaSourceId = intent.extras?.getString("mediaSourceId")

        isControlsLocked = false
        updatePlayerOrientation()

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.attachWindow(window)

        binding.playerView.player = viewModel.player
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
                updateSkipSegmentButton(viewModel.uiState.value)
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        configureInsets(playerControls)
        configureInsets(lockedControls)

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        // Apply user-configured subtitle appearance to the embedded SubtitleView. The view is
        // created by the inflated exo_player_view.xml and exposed via the @id/exo_subtitles id.
        binding.playerView.subtitleView?.applySubtitleStyle(appPreferences)

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        val qualityButton = binding.playerView.findViewById<ImageButton>(R.id.btn_quality)
        val qualitySpace = binding.playerView.findViewById<Space>(R.id.space_quality)
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)
        val chaptersButton = binding.playerView.findViewById<ImageButton>(R.id.btn_chapters)
        val chaptersSpace = binding.playerView.findViewById<Space>(R.id.space_chapters)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        uiState.apply {
                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Media segment (#271)
                            currentSegment?.let { segment ->
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            }
                            updateSkipSegmentButton(this)

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            val playerControlView =
                                findViewById<PlayerControlView>(R.id.exo_controller)
                            val hasChapters = currentChapters.isNotEmpty()
                            val showChapterMarkers =
                                hasChapters &&
                                    appPreferences.getValue(appPreferences.playerChapterMarkers)
                            if (showChapterMarkers) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }
                            // Hide the spacer too so the controls bar does not end in dead space.
                            chaptersButton.isVisible = hasChapters
                            chaptersSpace.isVisible = hasChapters

                            // File Loaded
                            if (fileLoaded) {
                                audioButton.isEnabled = true
                                audioButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                subtitleButton.isEnabled = true
                                subtitleButton.imageAlpha = 255
                                speedButton.isEnabled = true
                                speedButton.imageAlpha = 255
                                qualityButton.isEnabled = true
                                qualityButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }

                            // Still watching prompt — show / hide a dialog driven by the
                            // ViewModel. The ViewModel is the source of truth for the timeout;
                            // the dialog is purely a render of the state.
                            if (showStillWatching && stillWatchingDialog == null) {
                                val dialog = StillWatchingDialogFragment()
                                stillWatchingDialog = dialog
                                dialog.show(supportFragmentManager, "stillwatchingdialog")
                            } else if (!showStillWatching && stillWatchingDialog != null) {
                                stillWatchingDialog?.dismissAllowingStateLoss()
                                stillWatchingDialog = null
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                            is PlayerEvents.PlayerError -> {
                                // Error is already logged in PlayerViewModel
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                // Keeps the position used for the stop report on next/previous within a second.
                launch {
                    while (true) {
                        viewModel.refreshPlayingSnapshot()
                        delay(1000L)
                    }
                }

                if (viewModel.shouldPollSegments()) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }
            }
        }

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        // A bitrate cap only makes sense against the server; offline playback
        // always plays the local file as-is.
        if (appPreferences.getValue(appPreferences.offlineMode)) {
            qualityButton.isVisible = false
            qualitySpace.isVisible = false
        } else {
            qualityButton.isEnabled = false
            qualityButton.imageAlpha = 75
        }

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        audioButton.setOnClickListener {
            TrackSelectionDialogFragment.newInstance(C.TRACK_TYPE_AUDIO)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            requestedOrientation = playerOrientation
            isControlsLocked = false
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment.newInstance(C.TRACK_TYPE_TEXT)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment()
                .show(supportFragmentManager, "speedselectiondialog")
        }

        qualityButton.setOnClickListener {
            QualitySelectionDialogFragment()
                .show(supportFragmentManager, "qualityselectiondialog")
        }

        chaptersButton.setOnClickListener {
            ChapterListDialogFragment()
                .show(supportFragmentManager, "chapterlistdialog")
        }

        pipButton.setOnClickListener { pictureInPicture() }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        if (itemId != null) {
            viewModel.initializePlayer(
                itemId = itemId,
                itemKind = itemKind ?: "",
                startFromBeginning = startFromBeginning,
                mediaSourceId = mediaSourceId,
            )
        }
        hideSystemUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        val mediaSourceId = intent.extras!!.getString("mediaSourceId")

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            mediaSourceId = mediaSourceId,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        // videoSize is VideoSize.UNKNOWN (0x0) until the file is loaded, which would yield a NaN
        // Rational and crash setAspectRatio. Bail with IAE so the existing call-site catches
        // (setPictureInPictureParams / enterPictureInPictureMode both wrap in try { } catch (_: IAE)).
        val videoSize = binding.playerView.player?.videoSize
        val aspectRatio =
            if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                Rational(
                    videoSize.width.coerceAtMost((videoSize.height * 2.39f).toInt()),
                    videoSize.height.coerceAtMost((videoSize.width * 2.39f).toInt()),
                )
            } else {
                throw IllegalArgumentException("Video size unknown, cannot build PiP params")
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // Background audio: keep playing without a picture. The player stays attached to
        // PlayerView: detaching it drops mpv's surface callbacks and closes the shutter.
        if (viewModel.isBackgroundAudioEnabled && viewModel.player.isPlaying) {
            viewModel.setVideoEnabled(false)
        }
    }

    override fun onDestroy() {
        viewModel.detachWindow()
        // Cancel both trickplay MainScopes so any in-flight tile fetches don't outlive the
        // activity. onDestroy is the right hook here: previewScrubListener and
        // playerGestureHelper are bound to this activity's view hierarchy (binding,
        // playerView) and the listener is attached to a view-owned TimeBar, so they must
        // survive across onStop/onStart (e.g. PiP, screen off) and are only safe to tear
        // down when the activity itself is being destroyed.
        previewScrubListener?.dispose()
        playerGestureHelper?.dispose()
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

                // Override auto brightness
                if (
                    appPreferences.getValue(appPreferences.playerGesturesVB) &&
                        appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)
                ) {
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                appPreferences.getValue(appPreferences.playerBrightness)
                        }
                }
            }
        }
    }
}
