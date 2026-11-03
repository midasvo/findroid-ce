package dev.jdtech.jellyfin.ce.watchnext

import android.net.Uri
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Watch Next candidates for the server's Continue Watching [resume] items and [nextUp]
 * episodes, in that order. Items that can't be shown in the row (no duration, nothing to resume)
 * are left out.
 */
fun watchNextCandidates(
    resume: List<FindroidItem>,
    nextUp: List<FindroidEpisode>,
): List<WatchNextEntry> =
    resume.mapNotNull { it.toWatchNextEntry(WatchNextKind.CONTINUE) } +
        nextUp.mapNotNull { it.toWatchNextEntry(WatchNextKind.NEXT) }

fun FindroidItem.toWatchNextEntry(kind: WatchNextKind): WatchNextEntry? {
    if (runtimeTicks <= 0) return null
    if (kind == WatchNextKind.CONTINUE && (played || playbackPositionTicks <= 0)) return null
    val durationMs = runtimeTicks / TICKS_PER_MS
    val positionMs = if (kind == WatchNextKind.CONTINUE) playbackPositionTicks / TICKS_PER_MS else 0
    return when (this) {
        is FindroidEpisode -> {
            // The episode's own thumbnail is landscape; the series poster is the fallback.
            val landscape = images.primary ?: images.showBackdrop
            WatchNextEntry(
                itemId = id.toString(),
                kind = kind,
                itemType = WatchNextItemType.EPISODE,
                title = seriesName.ifBlank { name },
                episodeTitle = name,
                seasonNumber = parentIndexNumber,
                episodeNumber = indexNumber,
                description = overview.take(MAX_DESCRIPTION_LENGTH),
                posterUri = (landscape ?: images.showPrimary)?.sized(portrait = landscape == null),
                posterIsPortrait = landscape == null,
                durationMs = durationMs,
                positionMs = positionMs,
                lastEngagementMs = lastPlayedDate?.toEpochMs(),
            )
        }
        is FindroidMovie -> {
            val landscape = images.backdrop
            WatchNextEntry(
                itemId = id.toString(),
                kind = kind,
                itemType = WatchNextItemType.MOVIE,
                title = name,
                description = overview.take(MAX_DESCRIPTION_LENGTH),
                posterUri = (landscape ?: images.primary)?.sized(portrait = landscape == null),
                posterIsPortrait = landscape == null,
                durationMs = durationMs,
                positionMs = positionMs,
                lastEngagementMs = lastPlayedDate?.toEpochMs(),
            )
        }
        else -> null
    }
}

/**
 * The launcher downloads the artwork itself, at a card's size. Asking the server for a small copy
 * saves the box from downloading and scaling full-size backdrops.
 */
private fun Uri.sized(portrait: Boolean): String =
    buildUpon()
        .appendQueryParameter(if (portrait) "fillHeight" else "fillWidth", ARTWORK_SIZE.toString())
        .appendQueryParameter("quality", ARTWORK_QUALITY.toString())
        .build()
        .toString()

/** The Jellyfin SDK reads dates into the device's time zone. */
private fun LocalDateTime.toEpochMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private const val TICKS_PER_MS = 10_000L
private const val MAX_DESCRIPTION_LENGTH = 300
private const val ARTWORK_SIZE = 480
private const val ARTWORK_QUALITY = 90
