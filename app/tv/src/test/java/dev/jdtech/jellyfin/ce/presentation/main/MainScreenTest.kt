package dev.jdtech.jellyfin.ce.presentation.main

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.jdtech.jellyfin.core.presentation.dummy.dummyCollections
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var settingsOpened = false
    private var usersOpened = false

    private fun setUpMainScreen(showFavorites: Boolean = true) {
        settingsOpened = false
        usersOpened = false

        rule.setContent {
            FindroidTheme {
                MainScreenLayout(
                    user = dummyUser,
                    server = dummyServer,
                    libraries = dummyCollections,
                    showFavorites = showFavorites,
                    navigateToSettings = { settingsOpened = true },
                    navigateToUsers = { usersOpened = true },
                    homeContent = {
                        Box(Modifier.fillMaxSize().testTag("home_screen_content").focusable())
                    },
                    searchContent = {
                        Box(Modifier.fillMaxSize().testTag("placeholder_search").focusable())
                    },
                    favoritesContent = {
                        Box(Modifier.fillMaxSize().testTag("placeholder_favorites").focusable())
                    },
                    libraryContent = { Box(Modifier.fillMaxSize().testTag("library_screen_content_${it.id}")) },
                )
            }
        }
    }

    private fun selectDrawerItem(testTag: String) {
        rule.onNodeWithTag(testTag).requestFocus()
        rule.onNodeWithTag(testTag).performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
    }

    @Test
    fun drawerItemsAreDisplayed() {
        setUpMainScreen()
        rule.waitForIdle()

        rule.onNodeWithTag("main_drawer_content").assertIsDisplayed()
        rule.onNodeWithTag("drawer_item_profile").assertIsDisplayed()
        rule.onNodeWithTag("drawer_item_search").assertIsDisplayed()
        rule.onNodeWithTag("drawer_item_home").assertIsDisplayed()
        rule.onNodeWithTag("drawer_item_favorites").assertIsDisplayed()
        rule.onNodeWithTag("drawer_item_settings").assertIsDisplayed()
    }

    @Test
    fun selectingSearchOpensSearchPlaceholder() {
        setUpMainScreen()
        rule.waitForIdle()

        selectDrawerItem("drawer_item_search")
        rule.onNodeWithTag("placeholder_search").assertIsDisplayed()
    }

    @Test
    fun selectingFavoritesOpensFavoritesPlaceholder() {
        setUpMainScreen()
        rule.waitForIdle()

        selectDrawerItem("drawer_item_favorites")
        rule.onNodeWithTag("placeholder_favorites").assertIsDisplayed()
    }

    @Test
    fun selectingLibraryOpensLibraryContent() {
        setUpMainScreen()
        rule.waitForIdle()

        val library = dummyCollections.first()
        selectDrawerItem("drawer_item_library_${library.id}")

        rule.onNodeWithTag("library_screen_content_${library.id}").assertIsDisplayed()
    }

    @Test
    fun selectingSettingsInvokesCallback() {
        setUpMainScreen()
        rule.waitForIdle()

        selectDrawerItem("drawer_item_settings")
        assertTrue(settingsOpened)
    }

    @Test
    fun selectingProfileInvokesUsersCallback() {
        setUpMainScreen()
        rule.waitForIdle()

        selectDrawerItem("drawer_item_profile")
        assertTrue(usersOpened)
    }

    @Test
    fun backOnNonHomeDestinationReturnsToHome() {
        setUpMainScreen()
        rule.waitForIdle()

        // Navigate to search
        selectDrawerItem("drawer_item_search")
        rule.onNodeWithTag("placeholder_search").assertIsDisplayed()

        // Press back via activity onBackPressedDispatcher
        rule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()

        // Should return to Home
        rule.onNode(hasTestTag("placeholder_search")).assertDoesNotExist()
        rule.onNodeWithTag("home_screen_content").assertIsDisplayed()
    }

    private fun pressBack() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    @Test
    fun selectingADestinationMovesFocusIntoTheContent() {
        setUpMainScreen()
        rule.waitForIdle()

        selectDrawerItem("drawer_item_search")
        rule.onNodeWithTag("placeholder_search").assertIsFocused()
    }

    @Test
    fun backFromTheContentOfADestinationFocusesHome() {
        setUpMainScreen()
        rule.waitForIdle()
        selectDrawerItem("drawer_item_favorites")
        rule.onNodeWithTag("placeholder_favorites").assertIsFocused()

        // The focused placeholder leaves the composition: focus must not get lost.
        pressBack()
        rule.onNodeWithTag("home_screen_content").assertIsFocused()
    }

    @Test
    fun backOnHomeOpensTheDrawerOnHomeAndASecondBackExits() {
        setUpMainScreen()
        rule.waitForIdle()
        rule.onNodeWithTag("home_screen_content").requestFocus()

        pressBack()
        rule.onNodeWithTag("drawer_item_home").assertIsFocused()
        var finishing = false
        rule.activityRule.scenario.onActivity { finishing = it.isFinishing }
        assertFalse(finishing)

        pressBack()
        rule.activityRule.scenario.onActivity { finishing = it.isFinishing }
        assertTrue(finishing)
    }

    @Test
    fun drawerFavoritesHiddenWhenDisabled() {
        setUpMainScreen(showFavorites = false)
        rule.waitForIdle()

        rule.onNode(hasTestTag("drawer_item_favorites")).assertDoesNotExist()
    }

    @Test
    fun disablingFavoritesWhileOnFavoritesDestinationReturnsToHome() {
        var showFavorites by mutableStateOf(true)
        rule.setContent {
            FindroidTheme {
                MainScreenLayout(
                    user = dummyUser,
                    server = dummyServer,
                    libraries = dummyCollections,
                    showFavorites = showFavorites,
                    navigateToSettings = { settingsOpened = true },
                    navigateToUsers = { usersOpened = true },
                    homeContent = {
                        Box(Modifier.fillMaxSize().testTag("home_screen_content").focusable())
                    },
                    searchContent = {
                        Box(Modifier.fillMaxSize().testTag("placeholder_search").focusable())
                    },
                    favoritesContent = {
                        Box(Modifier.fillMaxSize().testTag("placeholder_favorites").focusable())
                    },
                    libraryContent = { Box(Modifier.fillMaxSize().testTag("library_screen_content_${it.id}")) },
                )
            }
        }
        rule.waitForIdle()

        selectDrawerItem("drawer_item_favorites")
        rule.onNodeWithTag("placeholder_favorites").assertIsDisplayed()

        rule.runOnIdle { showFavorites = false }
        rule.waitForIdle()

        rule.onNode(hasTestTag("placeholder_favorites")).assertDoesNotExist()
        rule.onNodeWithTag("home_screen_content").assertIsDisplayed()
    }
}
