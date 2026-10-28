package dev.jdtech.jellyfin.film.presentation.season

import org.jellyfin.sdk.model.api.ItemFields
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the missing season-level download button.
 *
 * `SeasonScreen` gates the header download button on
 * `episodes.any { it.canDownload }`, but the Jellyfin server only populates
 * `BaseItemDto.CanDownload` on `GET /Shows/{id}/Episodes` when `CanDownload`
 * is explicitly requested in `fields` (see `DtoService` server-side). If
 * [seasonEpisodeFields] drops [ItemFields.CAN_DOWNLOAD], every episode
 * reports `canDownload = false` and the button never appears.
 */
class SeasonEpisodeFieldsTest {

    @Test
    fun `season episode request includes Overview`() {
        assertTrue(seasonEpisodeFields.contains(ItemFields.OVERVIEW))
    }

    @Test
    fun `season episode request includes CanDownload`() {
        assertTrue(
            "loadSeason must request ItemFields.CAN_DOWNLOAD, otherwise SeasonScreen can never show the season download button",
            seasonEpisodeFields.contains(ItemFields.CAN_DOWNLOAD),
        )
    }
}
