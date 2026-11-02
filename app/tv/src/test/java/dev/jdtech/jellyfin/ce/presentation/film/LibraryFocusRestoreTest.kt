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
import androidx.paging.PagingData
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.library.LibraryAction
import dev.jdtech.jellyfin.film.presentation.library.LibraryState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Open → Back → same card focused" for the CE library grid (#125, ADR 0003). A
 * SaveableStateHolder swaps the grid out and back in, the way Navigation 3 does for the entry below
 * the top of the back stack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryFocusRestoreTest {
    @get:Rule val rule = createComposeRule()

    private val movies: List<FindroidItem> =
        (0 until 40).map { dummyMovie.copy(id = UUID(0, it.toLong()), name = "Movie $it") }

    private var detailOpen by mutableStateOf(false)
    private var opened: FindroidItem? = null

    private fun setUpLibrary() {
        val items = flowOf(PagingData.from(movies))
        rule.setContent {
            val stateHolder = rememberSaveableStateHolder()
            FindroidTheme {
                if (detailOpen) {
                    stateHolder.SaveableStateProvider("detail") { Box {} }
                } else {
                    stateHolder.SaveableStateProvider("library") {
                        LibraryScreenLayout(
                            libraryName = "Movies",
                            state = LibraryState(items = items),
                            onAction = { action ->
                                if (action is LibraryAction.OnItemClick) {
                                    opened = action.item
                                    detailOpen = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun card(item: FindroidItem): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(libraryCardTag(item.id))) and isFocusable()

    @Test
    fun firstVisitFocusesTheFirstCard() {
        setUpLibrary()
        rule.waitForIdle()
        rule.onNode(card(movies[0])).assertIsFocused()
    }

    @Test
    fun backFocusesTheCardThatWasOpened() {
        setUpLibrary()
        val target = movies[27]

        // Scroll a few rows down (header is grid item 0) and focus a card there.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(1 + 27)
        rule.onNode(card(target)).requestFocus()
        rule.onNode(card(target)).assertIsFocused()

        detailOpen = true
        rule.waitForIdle()
        detailOpen = false
        rule.waitForIdle()

        rule.onNode(card(target)).assertIsFocused()
    }

}
