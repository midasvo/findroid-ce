package dev.jdtech.jellyfin.repository

/**
 * When a screen that comes back into view (ON_RESUME) loads again (#251): only when it never
 * loaded, something changed since ([DataChangeEvents.version]) or the data is older than
 * [maxAgeMs]. A plain back navigation therefore costs no request.
 *
 * With stopEventFollows, a resume while the player's stop is still to be reported is left alone:
 * the stop event reloads, and doing both would load twice.
 */
class ReloadPolicy(
    private val events: DataChangeEvents,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var loadedAt: Long? = null
    private var loadedVersion = -1L

    /** Call when a load starts; hand the result to [complete] when it succeeds. */
    fun begin(): Long = events.version.value

    fun complete(token: Long) {
        loadedAt = clock()
        loadedVersion = token
    }

    /** The screen itself caused a change and already shows it: that is not "dirty" for itself. */
    fun changedHere() {
        events.onChanged()
        if (loadedAt != null) loadedVersion = events.version.value
    }

    fun invalidate() {
        loadedAt = null
    }

    /**
     * [stopEventFollows]: the screen also reloads on the player's stop event (the phone does), so a
     * resume while that report is due is left alone.
     */
    fun shouldReload(stopEventFollows: Boolean = false): Boolean {
        val at = loadedAt ?: return true
        if (stopEventFollows && events.playbackPending) return false
        return events.version.value != loadedVersion || clock() - at > maxAgeMs
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 60_000L
    }
}
