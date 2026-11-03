package dev.jdtech.jellyfin.ce.watchnext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The launcher tells the app when the user removes one of its programs from the Watch Next row.
 * The item is remembered as dismissed, so the next sync doesn't add it back (see [planWatchNext]).
 */
@AndroidEntryPoint
class WatchNextRemovedReceiver : BroadcastReceiver() {
    @Inject lateinit var store: WatchNextStore

    @Inject lateinit var publisher: WatchNextPublisher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED) return
        val programId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1)
        if (programId < 0) return
        store.dismiss(programId, publisher.positionOf(programId))
    }
}
