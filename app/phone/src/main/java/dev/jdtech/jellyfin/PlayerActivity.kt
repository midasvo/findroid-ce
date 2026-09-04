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
import android.os.Handler
import android.os.Looper
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
import androidx.activity.viewModels
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.ChapterListDialogFragment
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

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    override val viewModel: PlayerViewModel by viewModels()
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false
    private var skipButtonTimeoutExpired: Boolean = true

    private lateinit var skipSegmentButton: Button

    private var stillWatchingDialog: StillWatchingDialogFragment? = null

    /**
     * The fold the window is currently straddling, if it is one we react to: half-opened, with the
     * crease running horizontally across the window. Null whenever the window is flat, unfolded,
     * or split by a vertical crease.
     */
    private var tabletopFold: FoldingFeature? = null

    /**
     * Whether the window is split by a separating fold at all, in either orientation. Broader than
     * [tabletopFold] on purpose: while the activity is still held in landscape, a device being set
     * down into tabletop posture reports its crease as *vertical*, because the device has rotated
     * and the framebuffer has not. That is the only signal available to decide the window should be
     * allowed to follow it round.
     */
    private var hasSeparatingFold: Boolean = false

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

    private val handler = Handler(Looper.getMainLooper())
    private val skipButtonTimeout = Runnable {
        if (!binding.playerView.isControllerFullyVisible) {
            skipSegmentButton.isVisible = false
            skipButtonTimeoutExpired = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.playerView.player = viewModel.player
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        isControlsLocked = false

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

        if (appPreferences.getValue(appPreferences.playerTabletopMode)) {
            observeFoldingPosture()
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

                            // Media segment
                            currentSegment?.let { segment ->
                                // Skip Button - text
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                // Skip Button - visibility
                                skipSegmentButton.isVisible = !isInPictureInPictureMode
                                if (skipSegmentButton.isVisible) {
                                    skipButtonTimeoutExpired = false
                                    handler.removeCallbacks(skipButtonTimeout)
                                    handler.postDelayed(
                                        skipButtonTimeout,
                                        viewModel.segmentsSkipButtonDuration * 1000,
                                    )
                                }
                                // Skip Button - onClick
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            } ?: run { skipSegmentButton.isVisible = false }

                            binding.playerView.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    if (skipButtonTimeoutExpired && currentSegment != null) {
                                        skipSegmentButton.visibility = visibility
                                    }
                                }
                            )

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
            isControlsLocked = false
            // Restores landscape when flat, but hands the window back to the sensor if we are
            // still folded — hardcoding landscape here would drop us out of tabletop posture.
            applyPlaybackOrientation()
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment.newInstance(C.TRACK_TYPE_TEXT)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment()
                .show(supportFragmentManager, "speedselectiondialog")
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

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
        hideSystemUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
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

    /**
     * Issue #51 — tabletop / flex posture on foldables.
     *
     * When the device is half-opened with a horizontal crease, the picture would otherwise be
     * centred in the window and bend across the hinge. Collect the window's layout info and, while
     * such a fold is present, confine the whole player to the pane above it.
     *
     * [FoldingFeature.isSeparating] rather than a state check on its own: it is what distinguishes
     * a crease the content must not cross from one it merely spans. Bounds arrive in window
     * coordinates, so this stays correct whatever orientation the activity settles in, which
     * matters because a Pixel-style fold reaches tabletop posture in portrait while a Z Fold
     * reaches it in landscape.
     */
    private fun observeFoldingPosture() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@PlayerActivity)
                    .windowLayoutInfo(this@PlayerActivity)
                    .collect { layoutInfo ->
                        val separating =
                            layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().filter {
                                it.isSeparating
                            }
                        hasSeparatingFold = separating.isNotEmpty()
                        tabletopFold =
                            separating.firstOrNull {
                                it.orientation == FoldingFeature.Orientation.HORIZONTAL
                            }
                        applyPlaybackOrientation()
                        applyTabletopLayout()
                    }
            }
        }
    }

    /**
     * Let the window follow the device while it is folded, and pin it back to landscape once it is
     * not.
     *
     * Without this the manifest's sensorLandscape keeps the window in landscape, and a device whose
     * tabletop posture is *portrait* — a Pixel-style fold, whose inner display is landscape when
     * open — never presents its crease horizontally, so [applyTabletopLayout] would never fire on
     * exactly the hardware that has no system-level flex mode to fall back on.
     *
     * FULL_SENSOR rather than FULL_USER because it follows the sensor regardless of the user's
     * rotation lock, which the manifest's sensorLandscape already does for this activity. Requiring
     * auto-rotate would make the feature silently absent for anyone who keeps it off.
     *
     * Deliberately does nothing while the controls are locked — that lock is an explicit user
     * request to stop the picture moving, and it outranks posture.
     */
    private fun applyPlaybackOrientation() {
        if (isControlsLocked || isInPictureInPictureMode) return

        val target =
            if (hasSeparatingFold) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // The layout-info flow re-emits on every window change; reassigning the same value would
        // churn the activity for nothing.
        if (requestedOrientation != target) {
            requestedOrientation = target
        }
    }

    /**
     * Pad the bottom of the player root down to the crease, which confines every child — video
     * surface, subtitles, controls and the gesture overlays alike — to the top pane in one step,
     * because FrameLayout resolves both match_parent and layout_gravity inside its padding.
     *
     * Skipped in picture-in-picture: the window there is a small floating one that no longer spans
     * the hinge, and the padding would eat most of it.
     */
    private fun applyTabletopLayout() {
        val root = binding.root
        val fold = tabletopFold
        val inset = if (fold == null || isInPictureInPictureMode) 0 else tabletopBottomInset(fold)

        if (root.paddingBottom != inset) {
            root.updatePadding(bottom = inset)
        }

        // A fold can be reported before the first layout pass, when the root still measures 0 and
        // tabletopBottomInset() cannot produce anything meaningful. Retry once it has been laid
        // out. Guarded on isLaidOut because doOnLayout runs immediately on an already-laid-out
        // view, which would otherwise recurse for a fold that legitimately yields no inset.
        if (fold != null && !root.isLaidOut) {
            root.doOnLayout { applyTabletopLayout() }
        }
    }

    /**
     * Distance from the crease to the bottom of the player root, in the root's own coordinates.
     *
     * Returns 0 — i.e. leave the player full-bleed, exactly as it behaves today — for anything that
     * does not describe a fold genuinely crossing the middle of the view. That covers the
     * pre-layout case and any window that only partially overlaps the hinge, so an unexpected
     * geometry degrades to current behaviour rather than to a broken layout.
     */
    private fun tabletopBottomInset(fold: FoldingFeature): Int {
        val root = binding.root
        val height = root.height
        if (height <= 0) return 0

        val location = IntArray(2)
        root.getLocationInWindow(location)
        val foldTop = fold.bounds.top - location[1]

        return if (foldTop <= 0 || foldTop >= height) 0 else height - foldTop
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        handler.removeCallbacks(skipButtonTimeout)
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

    override fun onDestroy() {
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
        applyPlaybackOrientation()
        applyTabletopLayout()
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
