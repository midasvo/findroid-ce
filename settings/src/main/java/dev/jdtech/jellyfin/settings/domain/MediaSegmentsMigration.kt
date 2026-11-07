package dev.jdtech.jellyfin.settings.domain

/**
 * CE (#271): maps the old global media segment switches onto one action per segment type. Pure,
 * so it is tested without SharedPreferences; [AppPreferences.migrateMediaSegmentActions] runs it
 * once.
 *
 * The values are the names of `dev.jdtech.jellyfin.models.MediaSegmentAction` and
 * `FindroidSegmentType`, which live in the data module, a module this one can't see.
 */
object MediaSegmentsMigration {
    const val SKIP = "SKIP"
    const val ASK = "ASK"
    const val IGNORE = "IGNORE"

    const val INTRO = "INTRO"
    const val OUTRO = "OUTRO"
    const val RECAP = "RECAP"
    const val PREVIEW = "PREVIEW"
    const val COMMERCIAL = "COMMERCIAL"

    /** The segment types that have their own setting. */
    val TYPES = listOf(INTRO, OUTRO, RECAP, PREVIEW, COMMERCIAL)

    /** What a fresh install gets, and what the old defaults migrate to. */
    val DEFAULTS =
        mapOf(INTRO to ASK, OUTRO to ASK, RECAP to IGNORE, PREVIEW to IGNORE, COMMERCIAL to IGNORE)

    private val ACTIONS = listOf(SKIP, ASK, IGNORE)

    /**
     * The action for every type in [TYPES]:
     * 1. a valid value in [explicit] (picked per type before this migration) wins;
     * 2. otherwise [autoSkip] on with the type in [autoSkipTypes] gives [SKIP];
     * 3. otherwise [skipButton] on with the type in [skipButtonTypes] gives [ASK];
     * 4. otherwise [IGNORE].
     *
     * With the old defaults (skip button on for Intro and Outro, auto skip off) that is
     * [DEFAULTS].
     */
    fun actions(
        explicit: Map<String, String?>,
        autoSkip: Boolean,
        autoSkipTypes: Set<String>,
        skipButton: Boolean,
        skipButtonTypes: Set<String>,
    ): Map<String, String> =
        TYPES.associateWith { type ->
            explicit[type]?.let(::parse)
                ?: when {
                    autoSkip && type in autoSkipTypes -> SKIP
                    skipButton && type in skipButtonTypes -> ASK
                    else -> IGNORE
                }
        }

    private fun parse(value: String): String? =
        ACTIONS.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
}
