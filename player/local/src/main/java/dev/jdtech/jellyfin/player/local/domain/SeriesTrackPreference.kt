package dev.jdtech.jellyfin.player.local.domain

import java.util.Locale

/** A track as the matcher sees it: just what survives from one file to the next. */
data class TrackCandidate(val language: String?, val title: String?)

/** The identifying part of a stored series track choice. */
data class StoredChoice(val language: String?, val title: String?, val positionInLanguage: Int?)

/**
 * Normalizes a language code to ISO 639-2, so the "en" ExoPlayer reports and the "eng" mpv reports
 * compare equal. Blank and undetermined codes give null.
 */
fun normalizeLanguage(code: String?): String? {
    val base = code?.trim()?.lowercase()?.split('-', '_')?.firstOrNull().orEmpty()
    if (base.isEmpty() || base == "und") return null
    if (base.length != 2 && base.length != 3) return base
    return try {
        Locale(base).isO3Language.ifEmpty { base }
    } catch (e: Exception) {
        base
    }
}

private fun sameTitle(a: String?, b: String?): Boolean {
    val left = a?.trim().orEmpty()
    val right = b?.trim().orEmpty()
    return left.isNotEmpty() && left.equals(right, ignoreCase = true)
}

/**
 * Finds the track in [candidates] that best matches [stored], or null when none does. In order of
 * preference: same language and title, same language and position within that language, first
 * track of the same language.
 */
fun matchSeriesTrack(stored: StoredChoice, candidates: List<TrackCandidate>): Int? {
    val language = normalizeLanguage(stored.language)
    val sameLanguage =
        candidates.indices.filter { normalizeLanguage(candidates[it].language) == language }

    sameLanguage
        .firstOrNull { sameTitle(candidates[it].title, stored.title) }
        ?.let {
            return it
        }
    if (language == null) return null
    stored.positionInLanguage?.let { position ->
        sameLanguage.getOrNull(position)?.let {
            return it
        }
    }
    return sameLanguage.firstOrNull()
}

/**
 * Describes the track at [index] in [candidates] for storing, or null when it has neither a
 * language nor a title and so cannot be recognized in another file.
 */
fun seriesTrackChoice(candidates: List<TrackCandidate>, index: Int): StoredChoice? {
    val candidate = candidates.getOrNull(index) ?: return null
    val language = normalizeLanguage(candidate.language)
    val title = candidate.title?.trim()?.ifEmpty { null }
    if (language == null && title == null) return null
    val position =
        language?.let { candidates.take(index).count { normalizeLanguage(it.language) == language } }
    return StoredChoice(language, title, position)
}
