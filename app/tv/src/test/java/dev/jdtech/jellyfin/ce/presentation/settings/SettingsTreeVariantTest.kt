package dev.jdtech.jellyfin.ce.presentation.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.presentation.enums.AppVariant
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsTreeVariantTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sp = context.getSharedPreferences("test_settings_prefs", Context.MODE_PRIVATE)
        appPreferences = AppPreferences(sp)
    }

    @Test
    fun `TV variant hides gestures, trickplay, pip, and background playback in player settings`() =
        runTest {
            val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
            tvViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

            val groups = tvViewModel.state.value.preferenceGroups
            val groupTitles = groups.mapNotNull { it.nameStringResource }

            assertFalse(
                "Gestures group must be hidden on TV",
                groupTitles.contains(SettingsR.string.gestures),
            )
            assertFalse(
                "Trickplay group must be hidden on TV",
                groupTitles.contains(SettingsR.string.trickplay),
            )
            assertFalse(
                "PiP group must be hidden on TV",
                groupTitles.contains(SettingsR.string.picture_in_picture),
            )
            assertFalse(
                "Background audio group must be hidden on TV",
                groupTitles.contains(SettingsR.string.pref_player_background_group),
            )

            // Seeking group must be present on TV
            assertTrue(
                "Seeking group must be present on TV",
                groupTitles.contains(SettingsR.string.seeking),
            )
        }

    @Test
    fun `Phone variant shows gestures, trickplay, pip, and background playback in player settings`() =
        runTest {
            val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
            phoneViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

            val groups = phoneViewModel.state.value.preferenceGroups
            val groupTitles = groups.mapNotNull { it.nameStringResource }

            assertTrue(
                "Gestures group must be present on Phone",
                groupTitles.contains(SettingsR.string.gestures),
            )
            assertTrue(
                "Trickplay group must be present on Phone",
                groupTitles.contains(SettingsR.string.trickplay),
            )
            assertTrue(
                "PiP group must be present on Phone",
                groupTitles.contains(SettingsR.string.picture_in_picture),
            )
            assertTrue(
                "Background audio group must be present on Phone",
                groupTitles.contains(SettingsR.string.pref_player_background_group),
            )
        }

    @Test
    fun `TV variant hides downloads category and offline mode at top level`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(emptyList())

        val preferences = tvViewModel.state.value.preferenceGroups.flatMap { it.preferences }
        val names = preferences.map { it.nameStringResource }

        assertFalse(
            "Offline mode switch must be hidden on TV",
            names.contains(SettingsR.string.offline_mode),
        )
        assertFalse(
            "Downloads category must be hidden on TV",
            names.contains(SettingsR.string.title_download),
        )
    }

    @Test
    fun `Phone variant shows downloads category and offline mode at top level`() = runTest {
        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(emptyList())

        val preferences = phoneViewModel.state.value.preferenceGroups.flatMap { it.preferences }
        val names = preferences.map { it.nameStringResource }

        assertTrue(
            "Offline mode switch must be present on Phone",
            names.contains(SettingsR.string.offline_mode),
        )
        assertTrue(
            "Downloads category must be present on Phone",
            names.contains(SettingsR.string.title_download),
        )
    }

    @Test
    fun `TV variant deduplicates media segment settings`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

        val mediaSegmentsGroup =
            tvViewModel.state.value.preferenceGroups.first {
                it.nameStringResource == SettingsR.string.media_segments
            }
        val names = mediaSegmentsGroup.preferences.map { it.nameStringResource }

        // Legacy global toggles must be absent on TV
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_skip_button))
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_skip_button_type))
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_skip_button_duration))
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_auto_skip))
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_mode))
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_type))
        assertFalse(
            names.contains(SettingsR.string.pref_player_media_segments_next_episode_threshold)
        )

        // Per-type actions must be present on TV
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_intro))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_outro))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_recap))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_preview))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_commercial))
    }

    @Test
    fun `Phone variant keeps all media segment settings`() = runTest {
        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

        val mediaSegmentsGroup =
            phoneViewModel.state.value.preferenceGroups.first {
                it.nameStringResource == SettingsR.string.media_segments
            }
        val names = mediaSegmentsGroup.preferences.map { it.nameStringResource }

        // All legacy toggles present
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_skip_button))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_skip_button_type))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_skip_button_duration))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_auto_skip))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_mode))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_type))
        assertTrue(
            names.contains(SettingsR.string.pref_player_media_segments_next_episode_threshold)
        )

        // All per-type actions present
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_intro))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_outro))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_recap))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_preview))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_action_commercial))
    }

    @Test
    fun `dump phone and tv settings trees`() = runTest {
        fun dumpTree(variant: AppVariant): String {
            val vm = SettingsViewModel(context, appPreferences, variant)
            val sb = StringBuilder()

            fun printGroups(path: List<Int>, indent: String) {
                vm.loadPreferences(path)
                val groups = vm.state.value.preferenceGroups
                for (group in groups) {
                    val groupTitle = group.nameStringResource?.let { context.resources.getResourceEntryName(it) } ?: "(unnamed group)"
                    sb.appendLine("${indent}Group: $groupTitle")
                    for (pref in group.preferences) {
                        val prefName = context.resources.getResourceEntryName(pref.nameStringResource)
                        sb.appendLine("${indent}  - $prefName (${pref::class.simpleName})")
                        if (pref is PreferenceCategory && pref.nestedPreferenceGroups.isNotEmpty()) {
                            printGroups(path + pref.nameStringResource, "$indent    ")
                        }
                    }
                }
            }

            printGroups(emptyList(), "")
            return sb.toString()
        }

        val phoneTree = dumpTree(AppVariant.PHONE)
        val tvTree = dumpTree(AppVariant.TV)
        println("=== PHONE SETTINGS TREE ===\n$phoneTree")
        println("=== TV SETTINGS TREE ===\n$tvTree")
    }
}
