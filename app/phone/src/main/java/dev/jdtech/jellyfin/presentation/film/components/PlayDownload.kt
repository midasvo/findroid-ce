package dev.jdtech.jellyfin.presentation.film.components

import android.content.Context
import android.content.Intent
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import org.jellyfin.sdk.model.api.BaseItemKind

/** Whether [playDownloadedItem] can start [item] directly. */
fun canPlayDirectly(item: FindroidItem): Boolean = item is FindroidMovie || item is FindroidEpisode

/**
 * Resumes [item] in the player. The player picks the downloaded copy over streaming on its own.
 */
fun Context.playDownloadedItem(item: FindroidItem) {
    val kind =
        when (item) {
            is FindroidMovie -> BaseItemKind.MOVIE
            is FindroidEpisode -> BaseItemKind.EPISODE
            else -> return
        }
    val intent =
        Intent(this, PlayerActivity::class.java)
            .putExtra("itemId", item.id.toString())
            .putExtra("itemKind", kind.serialName)
            .putExtra("startFromBeginning", false)
    startActivity(intent)
}
