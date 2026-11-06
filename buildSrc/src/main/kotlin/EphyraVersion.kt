/**
 * CE-owned: the version of Ephyra. Releases follow semver and are tagged `v<NAME>`; the release
 * skill (.claude/skills/release) bumps NAME. Upstream's `Versions.APP_NAME` / `APP_CODE` are not
 * used, so syncing with Findroid never changes our version.
 */
object EphyraVersion {
    const val NAME = "2.1.0"

    private val parts =
        NAME.split(".").map { it.toInt() }.also {
            require(it.size == 3 && it[1] < 100 && it[2] < 100) { "NAME must be X.Y.Z with Y, Z < 100" }
        }
    val MAJOR = parts[0]
    val MINOR = parts[1]
    val PATCH = parts[2]

    /**
     * Phone versionCode (and TV + 1). Steps of 100 per patch leave room below the next version,
     * which the TV release uses: phone and TV share one application ID (ADR 0005).
     *
     * F-Droid's checkupdates parses CODE via regex: const val CODE = (\d+)
     */
    const val CODE = 2010000
    const val TV_CODE = 2010001

    init {
        val expected = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100
        check(CODE == expected) {
            "EphyraVersion.CODE ($CODE) must match MAJOR*1M + MINOR*10k + PATCH*100 ($expected)"
        }
        check(TV_CODE == expected + 1) {
            "EphyraVersion.TV_CODE ($TV_CODE) must match CODE + 1 (${expected + 1})"
        }
    }

    /** Betas are builds towards the next version, so they sort after NAME and before any release. */
    val NEXT_PATCH = "$MAJOR.$MINOR.${PATCH + 1}"
}
