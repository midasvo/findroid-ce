package dev.jdtech.jellyfin.ce.presentation.film

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.requestFocus
import dev.jdtech.jellyfin.core.R as CoreR
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
 * "Open → Back → same card focused" for the CE collection grid (#129, ADR 0003).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CollectionFocusRestoreTest {
    @get:Rule val rule = createComposeRule()

    private val movies: List<FindroidItem> =
        (0 until 20).map { dummyMovie.copy(id = UUID(0, it.toLong()), name = "Movie $it") }

    private var detailOpen by mutableStateOf(false)
    private var opened: FindroidItem? = null

    private fun setUpCollection() {
        val sections =
            listOf(
                CollectionSection(
                    id = 0,
                    name = UiText.StringResource(CoreR.string.movies_label),
                    items = movies,
                )
            )
        rule.setContent {
            val stateHolder = rememberSaveableStateHolder()
            FindroidTheme {
                if (detailOpen) {
                    stateHolder.SaveableStateProvider("detail") { Box {} }
                } else {
                    stateHolder.SaveableStateProvider("collection") {
                        CollectionScreenLayout(
                            collectionName = "Marvel",
                            state = CollectionState(sections = sections, isLoading = false),
                            onItemClick = { item ->
                                opened = item
                                detailOpen = true
                            },
                        )
                    }
                }
            }
        }
    }

    private fun card(item: FindroidItem): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(collectionCardTag(item.id))) and isFocusable()

    @Test
    fun firstVisitFocusesTheFirstCard() {
        setUpCollection()
        rule.waitForIdle()
        rule.onNode(card(movies[0])).assertIsFocused()
    }

    @Test
    fun backFocusesTheCardThatWasOpened() {
        setUpCollection()
        val target = movies[12]

        // Scroll down (header is grid item 0) and focus a card there.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(1 + 12)
        rule.onNode(card(target)).requestFocus()
        rule.onNode(card(target)).assertIsFocused()

        detailOpen = true
        rule.waitForIdle()
        detailOpen = false
        rule.waitForIdle()

        rule.onNode(card(target)).assertIsFocused()
    }
}
