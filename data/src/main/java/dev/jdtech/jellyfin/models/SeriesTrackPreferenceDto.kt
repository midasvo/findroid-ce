package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import java.util.UUID

/**
 * The audio or subtitle track a user last picked by hand in a series, so the next episodes start
 * with the same choice. Tracks are described by language, title and position rather than by index
 * because every file numbers its streams differently.
 */
@Entity(tableName = "seriesTrackPreferences", primaryKeys = ["userId", "seriesId", "trackType"])
data class SeriesTrackPreferenceDto(
    val userId: UUID,
    val seriesId: UUID,
    /** The media3 track type, `C.TRACK_TYPE_AUDIO` or `C.TRACK_TYPE_TEXT`. */
    val trackType: Int,
    /** True when the user switched the track type off (subtitles off). */
    val disabled: Boolean,
    val language: String? = null,
    val title: String? = null,
    /** Zero-based position among the tracks with the same language. */
    val positionInLanguage: Int? = null,
)
