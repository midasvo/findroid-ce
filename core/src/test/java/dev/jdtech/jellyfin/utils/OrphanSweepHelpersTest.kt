package dev.jdtech.jellyfin.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure helper functions extracted from [DownloaderImpl.sweepOrphans]:
 * [shouldDeleteOrphanFile] and [isUnderMountedRoot].
 */
class OrphanSweepHelpersTest {

    // ── shouldDeleteOrphanFile ────────────────────────────────────────────────

    @Test
    fun `non-download suffix is never deleted`() {
        assertFalse(
            shouldDeleteOrphanFile(
                path = "/storage/emulated/0/downloads/Name.sourceId.uuid.srt",
                knownPaths = emptySet(),
                liveDmPaths = emptySet(),
            ),
        )
    }

    @Test
    fun `subtitle path in knownPaths is kept`() {
        val path = "/storage/emulated/0/downloads/Show.sourceId.uuid.srt.download"
        assertFalse(
            shouldDeleteOrphanFile(
                path = path,
                knownPaths = setOf(path),
                liveDmPaths = emptySet(),
            ),
        )
    }

    @Test
    fun `path actively written by DM is kept`() {
        val path = "/storage/emulated/0/downloads/Show/S01/S01E01.mkv.download"
        assertFalse(
            shouldDeleteOrphanFile(
                path = path,
                knownPaths = emptySet(),
                liveDmPaths = setOf(path),
            ),
        )
    }

    @Test
    fun `untracked dot-download file with no live DM job is deleted`() {
        assertTrue(
            shouldDeleteOrphanFile(
                path = "/storage/emulated/0/downloads/Show/S01/junk.mkv.download",
                knownPaths = emptySet(),
                liveDmPaths = emptySet(),
            ),
        )
    }

    @Test
    fun `nested untracked orphan is deleted`() {
        assertTrue(
            shouldDeleteOrphanFile(
                path = "/storage/emulated/0/downloads/Movie/Movie (2024)/Movie (2024).mkv.download",
                knownPaths = emptySet(),
                liveDmPaths = emptySet(),
            ),
        )
    }

    // ── isUnderMountedRoot ────────────────────────────────────────────────────

    @Test
    fun `path under mounted root returns true`() {
        val root = "/storage/emulated/0/Android/data/nl.midasvo.findroid.ce/files/downloads"
        val roots = listOf(root)
        assertTrue(
            isUnderMountedRoot(
                path = "$root/Show/S01/S01E01.mkv.download",
                mountedRoots = roots,
            ),
        )
    }

    @Test
    fun `path not under any mounted root returns false`() {
        val roots = listOf("/storage/emulated/0/Android/data/nl.midasvo.findroid.ce/files/downloads")
        assertFalse(
            isUnderMountedRoot(
                path = "/storage/sdcard1/Android/data/nl.midasvo.findroid.ce/files/downloads/Movie.mkv",
                mountedRoots = roots,
            ),
        )
    }

    @Test
    fun `empty mounted roots list returns false`() {
        assertFalse(
            isUnderMountedRoot(
                path = "/storage/emulated/0/downloads/anything.mkv.download",
                mountedRoots = emptyList(),
            ),
        )
    }

    @Test
    fun `path equal to root but without trailing slash does not match`() {
        // The check requires startsWith("$root/") — a path equal to the root itself
        // (no trailing separator) must not be considered "under" the root.
        val roots = listOf("/storage/emulated/0/downloads")
        assertFalse(
            isUnderMountedRoot(
                path = "/storage/emulated/0/downloads",
                mountedRoots = roots,
            ),
        )
    }
}
