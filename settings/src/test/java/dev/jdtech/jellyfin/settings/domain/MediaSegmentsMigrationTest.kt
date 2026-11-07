package dev.jdtech.jellyfin.settings.domain

import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.ASK
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.COMMERCIAL
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.IGNORE
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.INTRO
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.OUTRO
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.PREVIEW
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.RECAP
import dev.jdtech.jellyfin.settings.domain.MediaSegmentsMigration.SKIP
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSegmentsMigrationTest {
    private fun migrate(
        explicit: Map<String, String?> = emptyMap(),
        autoSkip: Boolean = false,
        autoSkipTypes: Set<String> = setOf(INTRO, OUTRO),
        skipButton: Boolean = true,
        skipButtonTypes: Set<String> = setOf(INTRO, OUTRO),
    ) = MediaSegmentsMigration.actions(explicit, autoSkip, autoSkipTypes, skipButton, skipButtonTypes)

    @Test
    fun `old defaults give ask for intro and outro and nothing for the rest`() {
        assertEquals(
            mapOf(
                INTRO to ASK,
                OUTRO to ASK,
                RECAP to IGNORE,
                PREVIEW to IGNORE,
                COMMERCIAL to IGNORE,
            ),
            migrate(),
        )
        assertEquals(MediaSegmentsMigration.DEFAULTS, migrate())
    }

    @Test
    fun `auto skip wins over the skip button for a type in both lists`() {
        val actions =
            migrate(
                autoSkip = true,
                autoSkipTypes = setOf(INTRO),
                skipButtonTypes = setOf(INTRO, OUTRO),
            )
        assertEquals(SKIP, actions[INTRO])
        assertEquals(ASK, actions[OUTRO])
    }

    @Test
    fun `type lists decide which types are affected`() {
        val actions =
            migrate(
                autoSkip = true,
                autoSkipTypes = setOf(RECAP, PREVIEW),
                skipButton = true,
                skipButtonTypes = setOf(COMMERCIAL),
            )
        assertEquals(
            mapOf(
                INTRO to IGNORE,
                OUTRO to IGNORE,
                RECAP to SKIP,
                PREVIEW to SKIP,
                COMMERCIAL to ASK,
            ),
            actions,
        )
    }

    @Test
    fun `switches off give nothing even when the lists match`() {
        val actions = migrate(autoSkip = false, skipButton = false)
        assertEquals(MediaSegmentsMigration.TYPES.associateWith { IGNORE }, actions)
    }

    @Test
    fun `a per-type value picked before wins over the old switches`() {
        val actions = migrate(explicit = mapOf(INTRO to IGNORE, OUTRO to "skip"), autoSkip = true)
        assertEquals(IGNORE, actions[INTRO])
        assertEquals(SKIP, actions[OUTRO])
    }

    @Test
    fun `an absent or unknown per-type value falls back to the old switches`() {
        val actions = migrate(explicit = mapOf(INTRO to null, OUTRO to "MAYBE", RECAP to " "))
        assertEquals(ASK, actions[INTRO])
        assertEquals(ASK, actions[OUTRO])
        assertEquals(IGNORE, actions[RECAP])
    }

    @Test
    fun `the unknown segment type in the old lists is dropped`() {
        val actions = migrate(skipButtonTypes = setOf("UNKNOWN"))
        assertEquals(MediaSegmentsMigration.TYPES.toSet(), actions.keys)
        assertEquals(MediaSegmentsMigration.TYPES.associateWith { IGNORE }, actions)
    }
}
