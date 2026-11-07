package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import java.io.File
import timber.log.Timber

/**
 * CE (#271): fake media segments for testing the skip button against a server without any (the
 * public demo server has none). Debuggable builds only: release and staging builds never read the
 * file.
 *
 * Put one segment per line in `files/fake_segments.txt` of the debug app, as `TYPE,start,end` in
 * seconds, for example:
 * ```
 * adb shell "run-as nl.midasvo.findroid.ce.debug sh -c 'printf \"INTRO,10,40\\nOUTRO,60,9999\\n\" > files/fake_segments.txt'"
 * ```
 * The segments then apply to every item. An `end` past the runtime is clamped by the player.
 * Delete the file to go back to the server's segments.
 */
object DebugSegments {
    private const val FILE_NAME = "fake_segments.txt"

    /** The fake segments, or null when there are none or this isn't a debuggable build. */
    fun read(context: Context): List<FindroidSegment>? {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return null
        return try {
            parse(file.readText()).also { Timber.w("Using fake media segments: %s", it) }
        } catch (e: Exception) {
            Timber.e(e, "Couldn't read %s", FILE_NAME)
            null
        }
    }

    /** `TYPE,startSeconds,endSeconds` per line; blank lines and lines starting with # skipped. */
    fun parse(text: String): List<FindroidSegment> =
        text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val (type, start, end) = line.split(",").map { it.trim() }
                FindroidSegment(
                    type = FindroidSegmentType.valueOf(type.uppercase()),
                    startTicks = start.toLong() * 1000L,
                    endTicks = end.toLong() * 1000L,
                )
            }
            .toList()
}
