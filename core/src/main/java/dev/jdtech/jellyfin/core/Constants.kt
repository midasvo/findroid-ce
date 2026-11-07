package dev.jdtech.jellyfin.core

object Constants {
    // player
    const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
    const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24

    // CE (R6, T6, upstream #1144): on a player at least this wide and tall (dp), vertical swipes
    // for brightness and volume only start within an edge zone of
    // clamp(width * FRACTION, MIN, MAX) dp from the left or right edge; a vertical swipe in the
    // middle does nothing. Smaller players keep the left and right halves. The height bound keeps
    // phones in landscape (900+ dp wide but ~410 dp tall, compact height) on the halves.
    const val GESTURE_EDGE_ZONE_MIN_PLAYER_WIDTH = 840
    const val GESTURE_EDGE_ZONE_MIN_PLAYER_HEIGHT = 480
    const val GESTURE_EDGE_ZONE_FRACTION = 0.2f
    const val GESTURE_EDGE_ZONE_MIN = 96f
    const val GESTURE_EDGE_ZONE_MAX = 240f
    const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    const val ZOOM_SCALE_BASE = 1f
    const val ZOOM_SCALE_THRESHOLD = 0.01f

    // downloads
    const val DOWNLOAD_POLL_INTERVAL_MS = 1000L
    const val TICKS_PER_MINUTE = 600_000_000L

    // favorites
    const val FAVORITE_TYPE_MOVIES = 0
    const val FAVORITE_TYPE_SHOWS = 1
    const val FAVORITE_TYPE_EPISODES = 2
}
