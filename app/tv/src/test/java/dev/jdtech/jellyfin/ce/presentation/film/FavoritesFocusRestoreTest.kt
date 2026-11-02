package dev.jdtech.jellyfin.ce.presentation.film

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focus tests and empty state verification for the CE Favorites screen (#137, ADR 0003).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1920dp-h1080dp")
class FavoritesFocusRestoreTest {
    @get:Rule val rule = createComposeRule()

    private val movies: List<FindroidItem> =
        (0 until 5).map { dummyMovie.copy(id = UUID(0, it.toLong()), name = "Movie $it") }
    private val episodes: List<FindroidItem> =
        (0 until 3).map { dummyEpisode.copy(id = UUID(1, it.toLong()), name = "Episode $it") }

    private var detailOpen by mutableStateOf(false)
    private var opened: FindroidItem? = null

    private fun setUpFavorites(state: CollectionState) {
        rule.setContent {
            val stateHolder = rememberSaveableStateHolder()
            FindroidTheme {
                if (detailOpen) {
                    stateHolder.SaveableStateProvider("detail") { Box {} }
                } else {
                    stateHolder.SaveableStateProvider("favorites") {
                        FavoritesScreenLayout(
                            state = state,
                            onItemClick = { item ->
                                opened = item
                                detailOpen = true
                            },
                            onRetry = {},
                        )
                    }
                }
            }
        }
    }

    private fun card(item: FindroidItem): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(favoritesCardTag(item.id))) and isFocusable()

    @Test
    fun firstVisitFocusesTheFirstCard() {
        val state =
            CollectionState(
                sections =
                    listOf(
                        CollectionSection(
                            id = Constants.FAVORITE_TYPE_MOVIES,
                            name = UiText.StringResource(CoreR.string.movies_label),
                            items = movies,
                        ),
                        CollectionSection(
                            id = Constants.FAVORITE_TYPE_EPISODES,
                            name = UiText.StringResource(CoreR.string.episodes_label),
                            items = episodes,
                        ),
                    )
            )
        setUpFavorites(state)
        rule.waitForIdle()

        rule.onNode(card(movies[0])).assertIsFocused()
    }

    @Test
    fun backFocusesTheCardThatWasOpened() {
        val state =
            CollectionState(
                sections =
                    listOf(
                        CollectionSection(
                            id = Constants.FAVORITE_TYPE_MOVIES,
                            name = UiText.StringResource(CoreR.string.movies_label),
                            items = movies,
                        ),
                        CollectionSection(
                            id = Constants.FAVORITE_TYPE_EPISODES,
                            name = UiText.StringResource(CoreR.string.episodes_label),
                            items = episodes,
                        ),
                    )
            )
        setUpFavorites(state)
        rule.waitForIdle()

        val target = movies[2]
        rule.onNode(card(target)).requestFocus()
        rule.onNode(card(target)).assertIsFocused()

        detailOpen = true
        rule.waitForIdle()
        detailOpen = false
        rule.waitForIdle()

        rule.onNode(card(target)).assertIsFocused()
    }

    @Test
    fun emptyStateDisplaysExplanationAndIsFocused() {
        val state = CollectionState(isLoading = false, sections = emptyList())
        setUpFavorites(state)
        rule.waitForIdle()

        rule.onNodeWithTag(FAVORITES_EMPTY_TAG).assertIsDisplayed()
        rule.onNodeWithTag(FAVORITES_EMPTY_TAG).assertIsFocused()
        rule.onNode(hasText("You have no favorites")).assertIsDisplayed()
        rule.onNode(
                hasText(
                    "Select the heart icon on any movie, show, or episode to add it to your favorites."
                )
            )
            .assertIsDisplayed()
    }
}
