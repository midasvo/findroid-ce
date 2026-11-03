package dev.jdtech.jellyfin.ce.watchnext

import android.content.Context
import android.content.Intent
import dev.jdtech.jellyfin.MainActivity

/**
 * What a Watch Next program opens: the item, resumed in the player. The user id keeps a program
 * left over from another user from playing in this user's session.
 */
data class WatchNextLink(
    val itemId: String,
    val itemType: WatchNextItemType,
    val userId: String,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_PLAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_ITEM_ID, itemId)
            .putExtra(EXTRA_ITEM_TYPE, itemType.name)
            .putExtra(EXTRA_USER_ID, userId)

    companion object {
        const val ACTION_PLAY = "nl.midasvo.findroid.ce.action.WATCH_NEXT_PLAY"
        private const val EXTRA_ITEM_ID = "watch_next_item_id"
        private const val EXTRA_ITEM_TYPE = "watch_next_item_type"
        private const val EXTRA_USER_ID = "watch_next_user_id"

        fun fromIntent(intent: Intent?): WatchNextLink? {
            if (intent?.action != ACTION_PLAY) return null
            return WatchNextLink(
                itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return null,
                itemType =
                    WatchNextItemType.entries.firstOrNull {
                        it.name == intent.getStringExtra(EXTRA_ITEM_TYPE)
                    } ?: return null,
                userId = intent.getStringExtra(EXTRA_USER_ID) ?: return null,
            )
        }
    }
}
