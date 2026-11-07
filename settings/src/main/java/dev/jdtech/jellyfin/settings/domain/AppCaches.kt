package dev.jdtech.jellyfin.settings.domain

/**
 * What the Clear cache row in settings empties: the image caches and the remembered Home rows.
 * Each app binds its own, since the caches live outside this module.
 */
interface AppCaches {
    suspend fun clear()

    /** For previews and tests, where there is nothing to clear. */
    object None : AppCaches {
        override suspend fun clear() = Unit
    }
}
