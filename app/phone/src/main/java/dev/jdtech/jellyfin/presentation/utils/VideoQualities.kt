package dev.jdtech.jellyfin.presentation.utils

/**
 * The bitrate ladder offered by quality selectors (player streaming quality and
 * download quality), taken from jellyfin-web's quality options. 0 = no cap: the
 * server uses the original file whenever the profile allows it.
 */
object VideoQualities {
    val bitrates: List<Int> = listOf(
        0,
        120_000_000,
        80_000_000,
        40_000_000,
        20_000_000,
        10_000_000,
        8_000_000,
        6_000_000,
        4_000_000,
        3_000_000,
        1_500_000,
        720_000,
    )

    /**
     * Display label for a ladder rung, or null for the 0 ("original") rung —
     * callers localize that one themselves.
     */
    fun label(bitrate: Int): String? = when (bitrate) {
        120_000_000 -> "4K - 120 Mbps"
        80_000_000 -> "4K - 80 Mbps"
        40_000_000 -> "1080p - 40 Mbps"
        20_000_000 -> "1080p - 20 Mbps"
        10_000_000 -> "1080p - 10 Mbps"
        8_000_000 -> "720p - 8 Mbps"
        6_000_000 -> "720p - 6 Mbps"
        4_000_000 -> "720p - 4 Mbps"
        3_000_000 -> "480p - 3 Mbps"
        1_500_000 -> "480p - 1.5 Mbps"
        720_000 -> "360p - 720 kbps"
        else -> null
    }
}
