package dev.jdtech.jellyfin.film.presentation.homerows

sealed interface HomeRowsAction {
    data class OnVisibleChange(val key: String, val visible: Boolean) : HomeRowsAction

    data class OnMoveUp(val key: String) : HomeRowsAction

    data class OnMoveDown(val key: String) : HomeRowsAction

    data object OnRetryClick : HomeRowsAction

    data object OnBackClick : HomeRowsAction
}
