package dev.jdtech.jellyfin.models

/**
 * The URL of the user's profile picture on the server at [baseUrl], or null when the user has none.
 * The server hands it out without authentication.
 */
fun User.primaryImageUrl(baseUrl: String?): String? {
    val tag = primaryImageTag ?: return null
    val base = baseUrl?.trimEnd('/') ?: return null
    return "$base/Users/$id/Images/Primary?tag=$tag"
}
