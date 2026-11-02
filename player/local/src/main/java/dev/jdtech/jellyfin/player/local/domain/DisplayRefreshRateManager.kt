package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Window
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.lang.ref.WeakReference
import timber.log.Timber

data class DisplayModeCandidate(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
) {
    constructor(
        mode: Display.Mode
    ) : this(
        mode.modeId,
        mode.physicalWidth,
        mode.physicalHeight,
        mode.refreshRate,
    )
}

class DisplayRefreshRateManager(
    private val appPreferences: AppPreferences,
) {
    private var attachedWindow: WeakReference<Window>? = null
    private var lastDetectedFrameRate: Float? = null
    private var appliedModeId: Int? = null

    fun attachWindow(window: Window) {
        attachedWindow = WeakReference(window)
        lastDetectedFrameRate?.let { fps -> applyMatchingDisplayMode(fps) }
    }

    fun detachWindow() {
        reset()
        attachedWindow = null
    }

    fun onMediaItemTransition() {
        lastDetectedFrameRate = null
        appliedModeId = null
    }

    fun onVideoFrameRateDetected(frameRate: Float) {
        if (frameRate <= 0f || frameRate.isNaN()) return
        lastDetectedFrameRate = frameRate
        applyMatchingDisplayMode(frameRate)
    }

    fun reset() {
        val window = attachedWindow?.get() ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != 0) {
            Timber.i("Resetting preferredDisplayModeId to 0 (was ${attrs.preferredDisplayModeId})")
            attrs.preferredDisplayModeId = 0
            window.attributes = attrs
        }
        appliedModeId = null
    }

    private fun applyMatchingDisplayMode(frameRate: Float) {
        if (!appPreferences.getValue(appPreferences.playerTvMatchRefreshRate)) {
            return
        }
        val window = attachedWindow?.get() ?: return
        val display =
            window.decorView.display
                ?: (window.context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return

        val supportedModes = display.supportedModes?.map { DisplayModeCandidate(it) }.orEmpty()
        val currentMode = DisplayModeCandidate(display.mode)

        val targetMode = findBestDisplayMode(supportedModes, currentMode, frameRate)
        if (targetMode != null) {
            val targetModeId = targetMode.modeId
            if (
                targetModeId != currentMode.modeId &&
                    window.attributes.preferredDisplayModeId != targetModeId
            ) {
                Timber.i(
                    "Switching display mode for ${frameRate}fps to mode $targetModeId (${targetMode.refreshRate}Hz, current is ${currentMode.refreshRate}Hz)"
                )
                val attrs = window.attributes
                attrs.preferredDisplayModeId = targetModeId
                window.attributes = attrs
                appliedModeId = targetModeId
            }
        }
    }

    companion object {
        fun findBestDisplayMode(
            modes: List<DisplayModeCandidate>,
            currentMode: DisplayModeCandidate,
            targetFrameRate: Float,
        ): DisplayModeCandidate? {
            if (targetFrameRate <= 0f || targetFrameRate.isNaN() || modes.isEmpty()) return null

            // Prioritize modes matching current display resolution to avoid downscaling/upscaling
            val sameResolutionModes =
                modes.filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
            val candidates = if (sameResolutionModes.isNotEmpty()) sameResolutionModes else modes

            val matchingModes =
                candidates.mapNotNull { candidate ->
                    val ratio = candidate.refreshRate / targetFrameRate
                    val roundedRatio = Math.round(ratio)
                    if (roundedRatio < 1) return@mapNotNull null

                    val idealRate = targetFrameRate * roundedRatio
                    val diff = Math.abs(candidate.refreshRate - idealRate)
                    val normalizedDiff = diff / roundedRatio

                    // Only consider rates within 0.08 normalized difference
                    if (normalizedDiff < 0.08f) {
                        val isExact = normalizedDiff < 0.005f
                        val isCurrent = candidate.modeId == currentMode.modeId
                        MatchedMode(
                            candidate = candidate,
                            isExact = isExact,
                            normalizedDiff = normalizedDiff,
                            roundedRatio = roundedRatio,
                            isCurrent = isCurrent,
                        )
                    } else null
                }

            return matchingModes
                .sortedWith(
                    compareBy<MatchedMode> { !it.isExact }
                        .thenBy { it.normalizedDiff }
                        .thenBy { it.roundedRatio }
                        .thenBy { !it.isCurrent }
                )
                .firstOrNull()
                ?.candidate
        }

        private data class MatchedMode(
            val candidate: DisplayModeCandidate,
            val isExact: Boolean,
            val normalizedDiff: Float,
            val roundedRatio: Int,
            val isCurrent: Boolean,
        )
    }
}
