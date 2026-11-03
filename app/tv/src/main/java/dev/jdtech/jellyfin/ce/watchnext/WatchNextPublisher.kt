package dev.jdtech.jellyfin.ce.watchnext

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Reads and writes this app's programs in the launcher's Watch Next table (`TvProvider`). Every app
 * only sees its own programs there.
 */
@Singleton
class WatchNextPublisher @Inject constructor(@ApplicationContext private val context: Context) {
    private val helper = PreviewChannelHelper(context)
    private val json = Json { ignoreUnknownKeys = true }

    /** This app's programs, or null when the device has no Watch Next table. */
    @SuppressLint("RestrictedApi")
    fun readPublished(): List<PublishedProgram>? {
        val cursor =
            try {
                context.contentResolver.query(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    WatchNextProgram.PROJECTION,
                    null,
                    null,
                    null,
                )
            } catch (e: Exception) {
                Timber.w(e, "Watch Next: no TvProvider")
                null
            } ?: return null
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val program = WatchNextProgram.fromCursor(it)
                    add(
                        PublishedProgram(
                            programId = program.id,
                            itemId = program.internalProviderId,
                            browsable = program.isBrowsable,
                            entry = decode(program.internalProviderDataByteArray),
                        )
                    )
                }
            }
        }
    }

    /** Carries out [plan], and returns the program id per item after it. */
    fun apply(
        plan: WatchNextPlan,
        published: List<PublishedProgram>,
        userId: String,
    ): Map<String, Long> {
        val programs =
            published
                .filter { it.programId !in plan.deletes && it.itemId != null }
                .associate { it.itemId!! to it.programId }
                .toMutableMap()
        for (programId in plan.deletes) {
            context.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(programId),
                null,
                null,
            )
        }
        for ((programId, entry) in plan.updates) {
            helper.updateWatchNextProgram(entry.toProgram(userId), programId)
        }
        for (entry in plan.inserts) {
            val programId = helper.publishWatchNextProgram(entry.toProgram(userId))
            if (programId > 0) programs[entry.itemId] = programId
        }
        return programs
    }

    /** Removes all of this app's programs. */
    fun clear() {
        try {
            context.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
        } catch (e: Exception) {
            Timber.w(e, "Watch Next: could not clear the programs")
        }
    }

    /** The position the program [programId] showed, if it is still there. */
    fun positionOf(programId: Long): Long? =
        try {
            helper
                .getWatchNextProgram(programId)
                ?.lastPlaybackPositionMillis
                ?.takeIf { it >= 0 }
                ?.toLong()
        } catch (e: Exception) {
            null
        }

    @SuppressLint("RestrictedApi")
    private fun WatchNextEntry.toProgram(userId: String): WatchNextProgram {
        val builder =
            WatchNextProgram.Builder()
                .setWatchNextType(
                    when (kind) {
                        WatchNextKind.CONTINUE -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                        WatchNextKind.NEXT -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                    }
                )
                .setLastEngagementTimeUtcMillis(lastEngagementMs ?: System.currentTimeMillis())
                .setTitle(title)
                .setDescription(description)
                .setDurationMillis(durationMs.toIntMillis())
                .setIntentUri(
                    Uri.parse(
                        WatchNextLink(itemId, itemType, userId)
                            .toIntent(context)
                            .toUri(Intent.URI_INTENT_SCHEME)
                    )
                )
                .setInternalProviderId(itemId)
        builder.setInternalProviderData(json.encodeToString(WatchNextEntry.serializer(), this).toByteArray())
        if (kind == WatchNextKind.CONTINUE) {
            builder.setLastPlaybackPositionMillis(positionMs.toIntMillis())
        }
        posterUri?.let {
            builder
                .setPosterArtUri(Uri.parse(it))
                .setPosterArtAspectRatio(
                    if (posterIsPortrait) TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3
                    else TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
                )
        }
        when (itemType) {
            WatchNextItemType.MOVIE -> builder.setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
            WatchNextItemType.EPISODE -> {
                builder.setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
                episodeTitle?.let { builder.setEpisodeTitle(it) }
                seasonNumber?.let { builder.setSeasonNumber(it) }
                episodeNumber?.let { builder.setEpisodeNumber(it) }
            }
        }
        return builder.build()
    }

    private fun decode(data: ByteArray?): WatchNextEntry? =
        data?.let {
            try {
                json.decodeFromString(WatchNextEntry.serializer(), it.decodeToString())
            } catch (e: Exception) {
                null
            }
        }

    private fun Long.toIntMillis(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
}
