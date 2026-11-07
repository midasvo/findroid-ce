package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.models.MediaSegmentAction

/**
 * CE (#271): which media segment playback is in, and what the player should do about it.
 *
 * Pure: no Android, no player. [PlayerViewModel][dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel]
 * calls [update] from its once-a-second poll with the player's position and acts on the
 * [SegmentDecision].
 *
 * - A segment is active from its start until [END_MARGIN_MS] before its end, so the button doesn't
 *   flash up again in the last poll before the segment ends.
 * - An [MediaSegmentAction.ASK] segment shows the skip button from the position where playback
 *   entered it until [buttonDurationMs] of it has played ([SegmentDecision.Ask.inButtonWindow]).
 *   The UI shows it after that only while the controls are visible. Seeking back inside the
 *   segment opens the window again from there.
 * - An [MediaSegmentAction.SKIP] segment is skipped once per entry; with [autoSkipOnlyInPip] it is
 *   only skipped in picture-in-picture and asks otherwise.
 * - Skipping lands at the segment's end, or on the next item for an outro that ends within
 *   [nextEpisodeThresholdMs] of the runtime when there is a next item ([skipTarget]).
 */
class SegmentTracker(
    private val actions: Map<FindroidSegmentType, MediaSegmentAction>,
    private val buttonDurationMs: Long,
    private val nextEpisodeThresholdMs: Long,
    private val autoSkipOnlyInPip: Boolean,
) {
    private var segments: List<FindroidSegment> = emptyList()
    private var entered: FindroidSegment? = null
    private var enteredAtMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var autoSkipped: FindroidSegment? = null

    /** True when some segment type is set to skip or ask, so segments are worth loading. */
    val isActive: Boolean
        get() = actions.values.any { it != MediaSegmentAction.IGNORE }

    /** The current item's segments. Call on every item change; it forgets the previous item. */
    fun setSegments(segments: List<FindroidSegment>) {
        this.segments = segments
        entered = null
        autoSkipped = null
    }

    /** The configured action for [type]; types without a setting are ignored. */
    fun actionFor(type: FindroidSegmentType): MediaSegmentAction =
        actions[type] ?: MediaSegmentAction.IGNORE

    /** The segment playing at [positionMs], or null. */
    fun activeSegment(positionMs: Long): FindroidSegment? =
        segments.find { positionMs >= it.startTicks && positionMs < it.endTicks - END_MARGIN_MS }

    /**
     * What to do at [positionMs]. [durationMs] is the item's runtime (0 or less when unknown),
     * [hasNextItem] whether the queue has an item after this one.
     */
    fun update(
        positionMs: Long,
        durationMs: Long,
        hasNextItem: Boolean,
        inPictureInPicture: Boolean,
    ): SegmentDecision {
        val segment = activeSegment(positionMs)
        if (segment == null) {
            entered = null
            autoSkipped = null
            return SegmentDecision.None
        }
        if (segment != entered) {
            entered = segment
            enteredAtMs = positionMs
            autoSkipped = null
        } else if (positionMs < lastPositionMs - BACKWARD_SEEK_MS) {
            // Seeking back inside the segment: offer the button again from there.
            enteredAtMs = positionMs
        }
        lastPositionMs = positionMs

        val target = skipTarget(segment, durationMs, hasNextItem)
        return when (actionFor(segment.type)) {
            MediaSegmentAction.IGNORE -> SegmentDecision.None
            MediaSegmentAction.SKIP ->
                when {
                    autoSkipOnlyInPip && !inPictureInPicture -> ask(segment, target, positionMs)
                    autoSkipped == segment -> SegmentDecision.None
                    else -> {
                        autoSkipped = segment
                        SegmentDecision.AutoSkip(segment, target)
                    }
                }
            MediaSegmentAction.ASK -> ask(segment, target, positionMs)
        }
    }

    private fun ask(segment: FindroidSegment, target: SkipTarget, positionMs: Long) =
        SegmentDecision.Ask(
            segment = segment,
            target = target,
            inButtonWindow = positionMs - enteredAtMs < buttonDurationMs,
        )

    /**
     * Where skipping [segment] lands: the next item for an outro that runs to within
     * [nextEpisodeThresholdMs] of the end when [hasNextItem], its end otherwise.
     */
    fun skipTarget(segment: FindroidSegment, durationMs: Long, hasNextItem: Boolean): SkipTarget {
        val closesItem =
            segment.type == FindroidSegmentType.OUTRO &&
                hasNextItem &&
                durationMs > 0 &&
                segment.endTicks > durationMs - nextEpisodeThresholdMs
        return if (closesItem) SkipTarget.NextItem else SkipTarget.SeekTo(segment.endTicks)
    }

    companion object {
        /** A segment stops counting this long before its end. */
        const val END_MARGIN_MS = 100L

        /** A position this much behind the previous one is a seek back, not jitter. */
        const val BACKWARD_SEEK_MS = 1_000L

        /**
         * The target of an automatic skip. Moving on to the next item is autoplay, so with
         * [autoPlayNext] off the item ends there instead ([SkipTarget.EndItem]). A tap on the
         * button always goes to [target].
         */
        fun autoSkipTarget(target: SkipTarget, autoPlayNext: Boolean): SkipTarget =
            if (target == SkipTarget.NextItem && !autoPlayNext) SkipTarget.EndItem else target
    }
}

/** What the player should do about the segment at the current position. */
sealed interface SegmentDecision {
    /** Not in a segment, or in one that is ignored or already skipped. */
    data object None : SegmentDecision

    /** Offer a skip button. [inButtonWindow] is false once its time is up. */
    data class Ask(
        val segment: FindroidSegment,
        val target: SkipTarget,
        val inButtonWindow: Boolean,
    ) : SegmentDecision

    /** Skip now, without asking. */
    data class AutoSkip(val segment: FindroidSegment, val target: SkipTarget) : SegmentDecision
}

/** Where a skip lands. */
sealed interface SkipTarget {
    data class SeekTo(val positionMs: Long) : SkipTarget

    /** Go to the next item in the queue (an outro that runs to the end). */
    data object NextItem : SkipTarget

    /** End the item without going on (an automatic outro skip with autoplay off). */
    data object EndItem : SkipTarget
}
