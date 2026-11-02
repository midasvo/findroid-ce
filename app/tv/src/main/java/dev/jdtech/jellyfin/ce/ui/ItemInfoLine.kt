package dev.jdtech.jellyfin.ce.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.utils.format
import java.util.Date

/**
 * CE: `S1 E6 · date · runtime · time left · ends at` for an episode, `year · runtime · time left ·
 * ends at` for a movie and `year · rating` for a show, leaving out what isn't known.
 */
@Composable
fun itemInfoLine(item: FindroidItem): String {
    val context = LocalContext.current
    val remainingTicks = (item.runtimeTicks - item.playbackPositionTicks).coerceAtLeast(0)
    val inProgress = item.playbackPositionTicks > 0 && !item.played
    val parts = buildList {
        when (item) {
            is FindroidEpisode -> {
                add(
                    stringResource(
                        CoreR.string.season_episode_number,
                        item.parentIndexNumber,
                        item.indexNumber,
                    )
                )
                item.premiereDate?.let { add(it.format()) }
            }
            is FindroidMovie -> item.productionYear?.let { add(it.toString()) }
            is FindroidShow -> {
                item.productionYear?.let { add(it.toString()) }
                item.officialRating?.let { add(it) }
            }
        }
        // A show's runtime is that of one episode, so it says nothing about when it ends.
        if (item.runtimeTicks > 0 && item !is FindroidShow) {
            add(stringResource(CoreR.string.runtime_minutes, minutes(item.runtimeTicks)))
            if (inProgress) {
                add(stringResource(CoreR.string.runtime_minutes_left, minutes(remainingTicks)))
            }
            val endsAt = Date(System.currentTimeMillis() + remainingTicks / TICKS_PER_MILLISECOND)
            add(stringResource(CoreR.string.ends_at, DateFormat.getTimeFormat(context).format(endsAt)))
        }
    }
    return parts.joinToString(" · ")
}

/** Whole minutes, at least 1, so a short episode doesn't show "0 mins". */
private fun minutes(ticks: Long): Long = (ticks / TICKS_PER_MINUTE).coerceAtLeast(1)

private const val TICKS_PER_MILLISECOND = 10_000L
private const val TICKS_PER_MINUTE = 600_000_000L
