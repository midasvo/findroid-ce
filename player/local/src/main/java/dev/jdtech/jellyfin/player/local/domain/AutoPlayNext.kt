package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.settings.domain.Constants

/**
 * Whether the player moves on to the next item by itself when an item ends.
 *
 * Pure: the caller reads the preference and the server value, so this can be tested without
 * Android or a server.
 *
 * @param pref the "Play next episode automatically" preference.
 *   [Constants.PlayerAutoPlayNext.ON] and [Constants.PlayerAutoPlayNext.OFF] override the server.
 *   [Constants.PlayerAutoPlayNext.SERVER], `null` and unknown values follow [server].
 * @param server the Jellyfin user's `enableNextEpisodeAutoPlay`, or `null` when it is not known
 *   (offline, or the request failed). Unknown falls back to autoplay, the behaviour before the
 *   setting existed.
 */
fun resolveAutoPlayNext(pref: String?, server: Boolean?): Boolean =
    when (pref) {
        Constants.PlayerAutoPlayNext.ON -> true
        Constants.PlayerAutoPlayNext.OFF -> false
        else -> server ?: true
    }

/** True if [resolveAutoPlayNext] needs the server value for [pref], i.e. it is not overridden. */
fun autoPlayNextFollowsServer(pref: String?): Boolean =
    pref != Constants.PlayerAutoPlayNext.ON && pref != Constants.PlayerAutoPlayNext.OFF
