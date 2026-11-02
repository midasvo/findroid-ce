package dev.jdtech.jellyfin.ce.presentation.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.EntryPointAccessors
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.di.SubtitleStyleEntryPoint
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.domain.getTrackNames
import dev.jdtech.jellyfin.player.local.presentation.PlayerAction
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.player.ChapterListDialog
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.applySubtitleStyle
import java.util.UUID
import kotlinx.coroutines.delay

private enum class PlayerDialog {
    AUDIO,
    SUBTITLES,
    SPEED,
    CHAPTERS,
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * CE (#126, ADR 0004): the TV player screen, on the shared [PlayerViewModel]. It replaces the
 * upstream `ui/PlayerScreen` (ADR 0001): seeking with the remote, media keys, and audio, subtitle
 * and speed dialogs.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
    mediaSourceId: String? = null,
    navigateBack: () -> Unit,
) {
    val viewModel = hiltViewModel<PlayerViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val player = viewModel.player

    ObserveAsEvents(viewModel.eventsChannelFlow) { event ->
        when (event) {
            // Sent when the queue ends, or when an episode ends with autoplay off.
            is PlayerEvents.NavigateBack -> navigateBack()
            // Already logged in PlayerViewModel; phone does not surface it either.
            is PlayerEvents.PlayerError -> Unit
            is PlayerEvents.IsPlayingChanged -> Unit
        }
    }

    val context = LocalContext.current
    val appPreferences = remember {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                SubtitleStyleEntryPoint::class.java,
            )
            .appPreferences()
    }
    val seekSettings = remember {
        TvSeekSettings(
            backMs = appPreferences.getValue(appPreferences.playerTvSeekBackInc).toLongOrNull()
                ?: 10_000L,
            forwardMs =
                appPreferences.getValue(appPreferences.playerTvSeekForwardInc).toLongOrNull()
                    ?: 30_000L,
            okPauses = appPreferences.getValue(appPreferences.playerTvOkPauses),
        )
    }

    // Keep the screen on while the player is shown.
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

    // The media session is owned by PlayerViewModel; building a second one here would crash,
    // as Media3 requires session IDs to be unique.
    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> lifecycle = event }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Drives the skip button and auto-skip for media segments, as PlayerActivity does on phone.
    LaunchedEffect(Unit) {
        if (!viewModel.shouldPollSegments()) return@LaunchedEffect
        while (true) {
            viewModel.updateCurrentSegment()
            delay(1000L)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekIndicator by remember { mutableStateOf<SeekIndicator?>(null) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val dialogOpenerFocus = remember { PlayerDialog.entries.associateWith { FocusRequester() } }

    val segment = uiState.currentSegment
    val skipButtonShown = segment != null && !controlsVisible
    val currentSkipButtonShown by rememberUpdatedState(skipButtonShown)
    val currentProgress by rememberUpdatedState(progress)

    // Hide the controls after a few seconds without a key, unless paused or a dialog is open.
    LaunchedEffect(controlsVisible, interactions, progress.isPlaying, dialog, pendingSeekMs) {
        if (controlsVisible && progress.isPlaying && dialog == null && pendingSeekMs == null) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekIndicator) {
        if (seekIndicator != null) {
            delay(SEEK_INDICATOR_MS)
            seekIndicator = null
        }
    }
    // Focus follows the controls: the play button when they show, the screen (or the skip
    // button) when they hide, so the key handler always gets the keys.
    LaunchedEffect(controlsVisible, skipButtonShown) {
        if (controlsVisible) {
            focusWhenReady(playPauseFocus)
        } else if (!skipButtonShown) {
            focusWhenReady(rootFocus)
        }
    }

    val controls = remember {
        object : TvPlayerControls {
            override val controlsVisible: Boolean
                get() = controlsVisible

            override val skipButtonShown: Boolean
                get() = currentSkipButtonShown

            override val positionMs: Long
                get() = player.currentPosition.coerceAtLeast(0L)

            override val durationMs: Long
                get() = player.duration.takeIf { it != C.TIME_UNSET } ?: currentProgress.durationMs

            override fun showControls() {
                controlsVisible = true
                interactions++
            }

            override fun hideControls() {
                controlsVisible = false
            }

            override fun onUserInteraction() {
                interactions++
            }

            override fun showSeekIndicator(targetMs: Long, forward: Boolean) {
                seekIndicator = SeekIndicator(targetMs, forward)
            }

            override fun seekTo(positionMs: Long) {
                player.seekTo(positionMs)
            }

            override fun togglePlayPause() {
                if (player.isPlaying) player.pause() else player.play()
                showControls()
            }

            override fun play() = player.play()

            override fun pause() = player.pause()

            override fun next() {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }

            override fun previous() = player.seekToPrevious()

            override fun stop() = navigateBack()

            override fun openAudioDialog() {
                dialog = PlayerDialog.AUDIO
            }

            override fun openSubtitleDialog() {
                dialog = PlayerDialog.SUBTITLES
            }
        }
    }
    val keyHandler = remember { TvPlayerKeyHandler(controls, { seekSettings }) }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    keyHandler.onKeyEvent(event.key.nativeKeyCode, native.action, native.repeatCount)
                }
                .focusRequester(rootFocus)
                .focusable()
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).also { playerView ->
                    playerView.player = player
                    playerView.useController = false
                    viewModel.initializePlayer(
                        itemId = itemId,
                        itemKind = itemKind,
                        startFromBeginning = startFromBeginning,
                        mediaSourceId = mediaSourceId,
                    )
                    playerView.setBackgroundColor(
                        context.resources.getColor(android.R.color.black, context.theme)
                    )
                    // PlayerView creates its own SubtitleView when useController = false.
                    playerView.subtitleView?.applySubtitleStyle(appPreferences)
                }
            },
            update = {
                when (lifecycle) {
                    Lifecycle.Event.ON_PAUSE -> {
                        it.onPause()
                        it.player?.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> it.onResume()
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        seekIndicator?.let { indicator ->
            if (!controlsVisible) {
                SeekIndicatorView(
                    indicator = indicator,
                    durationMs = progress.durationMs,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        if (segment != null && !controlsVisible) {
            val skipFocus = remember { FocusRequester() }
            SkipButton(
                stringRes = uiState.currentSkipButtonStringRes,
                onClick = { viewModel.skipSegment(segment) },
                focusRequester = skipFocus,
            )
            LaunchedEffect(segment) { focusWhenReady(skipFocus) }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(
                title = uiState.currentItemTitle,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                isPlaying = progress.isPlaying,
                pendingSeekMs = pendingSeekMs,
                onPendingSeekChange = { pendingSeekMs = it },
                seekSettings = seekSettings,
                chapterMarkers =
                    remember(uiState.currentChapters, progress.durationMs) {
                        val duration = progress.durationMs
                        if (duration > 0) {
                            uiState.currentChapters.map { it.startPosition.toFloat() / duration }
                        } else {
                            emptyList()
                        }
                    },
                hasChapters = uiState.currentChapters.isNotEmpty(),
                playPauseFocus = playPauseFocus,
                dialogOpenerFocus = dialogOpenerFocus,
                onSeek = { player.seekTo(it) },
                onPlayPause = { controls.togglePlayPause() },
                onOpenDialog = { dialog = it },
                onInteraction = { interactions++ },
            )
        }
    }

    dialog?.let { open ->
        val close: () -> Unit = {
            dialog = null
            interactions++
        }
        when (open) {
            PlayerDialog.AUDIO,
            PlayerDialog.SUBTITLES -> {
                val trackType =
                    if (open == PlayerDialog.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                // Read once when the dialog opens; the tracks don't change while it's shown.
                val groups = remember {
                    player.currentTracks.groups.filter { it.type == trackType && it.isSupported }
                }
                val names = remember { groups.getTrackNames().toList() }
                val selected = groups.indexOfFirst { it.isSelected }
                // Subtitles can be turned off. Audio can't: silence is never what's wanted.
                val withNone = open == PlayerDialog.SUBTITLES
                TvSelectionDialog(
                    title =
                        stringResource(
                            if (withNone) PlayerR.string.select_subtitle_track
                            else PlayerR.string.select_audio_track
                        ),
                    options =
                        if (withNone) listOf(stringResource(PlayerR.string.none)) + names
                        else names,
                    selectedIndex = if (withNone) selected + 1 else selected.coerceAtLeast(0),
                    onSelect = { index ->
                        viewModel.switchToTrack(trackType, if (withNone) index - 1 else index)
                    },
                    onDismiss = close,
                )
            }
            PlayerDialog.SPEED ->
                TvSelectionDialog(
                    title = stringResource(PlayerR.string.select_playback_speed),
                    options = SPEEDS.map { "${it}x".replace(".0x", "x") },
                    selectedIndex = SPEEDS.indexOf(viewModel.playbackSpeed).coerceAtLeast(0),
                    onSelect = { viewModel.selectSpeed(SPEEDS[it]) },
                    onDismiss = close,
                )
            PlayerDialog.CHAPTERS ->
                ChapterListDialog(
                    chapters = uiState.currentChapters,
                    chapterImageUrl = { index -> viewModel.chapterImageUrl(index) },
                    onChapterSelected = { index ->
                        viewModel.onAction(PlayerAction.JumpToChapter(index))
                    },
                    onDismiss = close,
                )
        }
        // A dialog returns focus to the button that opened it. Opened with a media key while the
        // controls were hidden, there is no such button, and focus goes back to the screen.
        DisposableEffect(open) {
            onDispose {
                val target = if (controlsVisible) dialogOpenerFocus.getValue(open) else rootFocus
                target.requestFocus()
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    pendingSeekMs: Long?,
    onPendingSeekChange: (Long?) -> Unit,
    seekSettings: TvSeekSettings,
    chapterMarkers: List<Float>,
    hasChapters: Boolean,
    playPauseFocus: FocusRequester,
    dialogOpenerFocus: Map<PlayerDialog, FocusRequester>,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onOpenDialog: (PlayerDialog) -> Unit,
    onInteraction: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(
                    horizontal = MaterialTheme.spacings.default * 2,
                    vertical = MaterialTheme.spacings.large,
                )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatTime(pendingSeekMs ?: positionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (pendingSeekMs != null) MaterialTheme.colorScheme.primary else Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            TvSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                backStepMs = seekSettings.backMs,
                forwardStepMs = seekSettings.forwardMs,
                pendingMs = pendingSeekMs,
                onPendingChange = onPendingSeekChange,
                onSeek = onSeek,
                onInteraction = onInteraction,
                chapterMarkers = chapterMarkers,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                modifier = Modifier.focusRestorer(playPauseFocus).focusGroup(),
            ) {
                ControlButton(
                    icon = if (isPlaying) CoreR.drawable.ic_pause else CoreR.drawable.ic_play,
                    contentDescription = null,
                    focusRequester = playPauseFocus,
                    onClick = onPlayPause,
                    onFocus = onInteraction,
                )
                ControlButton(
                    icon = CoreR.drawable.ic_speaker,
                    contentDescription = stringResource(PlayerR.string.select_audio_track),
                    focusRequester = dialogOpenerFocus.getValue(PlayerDialog.AUDIO),
                    onClick = { onOpenDialog(PlayerDialog.AUDIO) },
                    onFocus = onInteraction,
                )
                ControlButton(
                    icon = CoreR.drawable.ic_closed_caption,
                    contentDescription = stringResource(PlayerR.string.select_subtitle_track),
                    focusRequester = dialogOpenerFocus.getValue(PlayerDialog.SUBTITLES),
                    onClick = { onOpenDialog(PlayerDialog.SUBTITLES) },
                    onFocus = onInteraction,
                )
                ControlButton(
                    icon = CoreR.drawable.ic_gauge,
                    contentDescription = stringResource(PlayerR.string.select_playback_speed),
                    focusRequester = dialogOpenerFocus.getValue(PlayerDialog.SPEED),
                    onClick = { onOpenDialog(PlayerDialog.SPEED) },
                    onFocus = onInteraction,
                )
                if (hasChapters) {
                    ControlButton(
                        icon = CoreR.drawable.ic_list,
                        contentDescription = stringResource(PlayerR.string.select_chapter),
                        focusRequester = dialogOpenerFocus.getValue(PlayerDialog.CHAPTERS),
                        onClick = { onOpenDialog(PlayerDialog.CHAPTERS) },
                        onFocus = onInteraction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: Int,
    contentDescription: String?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.focusRequester(focusRequester).onFocusChanged {
                if (it.isFocused) onFocus()
            },
    ) {
        Icon(painter = painterResource(id = icon), contentDescription = contentDescription)
    }
}

private data class SeekIndicator(val targetMs: Long, val forward: Boolean)

@Composable
private fun SeekIndicatorView(indicator: SeekIndicator, durationMs: Long, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(top = MaterialTheme.spacings.large)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(horizontal = MaterialTheme.spacings.medium, vertical = MaterialTheme.spacings.small),
    ) {
        Icon(
            painter =
                painterResource(
                    if (indicator.forward) CoreR.drawable.ic_fast_forward
                    else CoreR.drawable.ic_rewind
                ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        Text(
            text = "${formatTime(indicator.targetMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun SkipButton(stringRes: Int, onClick: () -> Unit, focusRequester: FocusRequester) {
    Box(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large).zIndex(1f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.focusRequester(focusRequester),
            glow =
                ButtonDefaults.glow(
                    focusedGlow = Glow(elevationColor = Color.Gray, elevation = 20.dp)
                ),
        ) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_skip_forward),
                contentDescription = null,
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(stringRes), color = Color.Black)
        }
    }
}

/** `m:ss`, or `h:mm:ss` from an hour. */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** An element that appears with the controls may not be laid out yet: retry for a few frames. */
private suspend fun focusWhenReady(requester: FocusRequester) {
    repeat(10) {
        if (requester.requestFocus()) return
        delay(16)
    }
}

private const val CONTROLS_HIDE_MS = 5_000L
private const val SEEK_INDICATOR_MS = 1_000L
