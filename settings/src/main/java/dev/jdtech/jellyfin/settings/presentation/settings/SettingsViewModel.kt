package dev.jdtech.jellyfin.settings.presentation.settings

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.R
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import dev.jdtech.jellyfin.settings.domain.models.PermissionDefinitions
import dev.jdtech.jellyfin.settings.domain.models.Preference as PreferenceBackend
import dev.jdtech.jellyfin.settings.presentation.enums.AppVariant
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceAppLanguage
import dev.jdtech.jellyfin.settings.domain.AppCaches
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceAction
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceDynamicSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceFileEdit
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceIntInput
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceLongInput
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMultiSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferencePermission
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSwitch
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val appVariant: AppVariant = AppVariant.PHONE,
    private val appCaches: AppCaches = AppCaches.None,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<SettingsEvent>()
    val events = eventsChannel.receiveAsFlow()

    private val permissionPreferences =
        PermissionDefinitions.all.map { permission ->
            PreferencePermission(permission = permission)
        }

    private val permissionsPreferenceGroup =
        PreferenceGroup(
            preferences =
                listOf(
                    PreferenceCategory(
                        nameStringResource = R.string.settings_category_permissions,
                        iconDrawableId = R.drawable.ic_shield_check,
                        onClick = {
                            viewModelScope.launch {
                                eventsChannel.send(
                                    SettingsEvent.NavigateToSettings(listOf(it.nameStringResource))
                                )
                            }
                        },
                        nestedPreferenceGroups =
                            listOf(PreferenceGroup(preferences = permissionPreferences)),
                    )
                )
        )

    private val topLevelPreferences =
        listOf(
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceSwitch(
                            nameStringResource = R.string.offline_mode,
                            descriptionStringRes = R.string.offline_mode_summary,
                            iconDrawableId = R.drawable.ic_server_off,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.RestartActivity)
                                }
                            },
                            backendPreference = appPreferences.offlineMode,
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_language,
                            iconDrawableId = R.drawable.ic_languages,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceAppLanguage(
                                                    nameStringResource = R.string.app_language,
                                                    iconDrawableId = R.drawable.ic_languages,
                                                    enabled =
                                                        Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.TIRAMISU,
                                                )
                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.settings_preferred_audio_language,
                                                    iconDrawableId = R.drawable.ic_speaker,
                                                    backendPreference =
                                                        appPreferences.preferredAudioLanguage,
                                                    options = R.array.languages,
                                                    optionValues = R.array.languages_values,
                                                    optionsIncludeNull = true,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .settings_preferred_subtitle_language,
                                                    iconDrawableId = R.drawable.ic_closed_caption,
                                                    backendPreference =
                                                        appPreferences.preferredSubtitleLanguage,
                                                    options = R.array.languages,
                                                    optionValues = R.array.languages_values,
                                                    optionsIncludeNull = true,
                                                ),
                                            )
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_interface,
                            iconDrawableId = R.drawable.ic_layout_dashboard,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        nameStringResource = R.string.settings_category_appearance,
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource = R.string.theme,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.theme,
                                                    onUpdate = { value ->
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.UpdateTheme(
                                                                    value ?: "system"
                                                                )
                                                            )
                                                        }
                                                    },
                                                    options = R.array.theme,
                                                    optionValues = R.array.theme_values,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.dynamic_colors,
                                                    descriptionStringRes =
                                                        R.string.dynamic_colors_summary,
                                                    enabled =
                                                        Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.S,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.dynamicColors,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.pref_tv_theme_music,
                                                    descriptionStringRes =
                                                        R.string.pref_tv_theme_music_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference = appPreferences.tvThemeMusic,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.home,
                                        preferences =
                                            listOf(
                                                // CE (#142): the rows, their order and which are
                                                // shown, per user and server. Replaces the
                                                // Display ... switches, which it migrates.
                                                PreferenceCategory(
                                                    nameStringResource = R.string.home_rows,
                                                    descriptionStringRes = R.string.home_rows_summary,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.NavigateToHomeRows
                                                            )
                                                        }
                                                    },
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.home_next_up_max_days,
                                                    descriptionStringRes =
                                                        R.string.home_next_up_max_days_summary,
                                                    backendPreference =
                                                        appPreferences.homeNextUpMaxDays,
                                                    suffixRes = R.string.home_next_up_max_days_suffix,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.pref_tv_watch_next,
                                                    descriptionStringRes =
                                                        R.string.pref_tv_watch_next_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference = appPreferences.tvWatchNext,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_show_favorites_tab,
                                                    descriptionStringRes =
                                                        R.string.pref_show_favorites_tab_summary,
                                                    backendPreference =
                                                        appPreferences.showFavoritesTab,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.extra_info,
                                                    descriptionStringRes =
                                                        R.string.extra_info_summary,
                                                    backendPreference =
                                                        appPreferences.displayExtraInfo,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.cast_and_crew,
                                                    descriptionStringRes =
                                                        R.string.cast_and_crew_summary,
                                                    backendPreference =
                                                        appPreferences.displayCastAndCrew,
                                                ),
                                            )
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_player,
                            iconDrawableId = R.drawable.ic_play,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceCategory(
                                                    nameStringResource = R.string.subtitle_style,
                                                    descriptionStringRes =
                                                        R.string.subtitle_style_summary,
                                                    iconDrawableId = R.drawable.ic_closed_caption,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent
                                                                    .NavigateToSubtitleStyle
                                                            )
                                                        }
                                                    },
                                                    // Surfaced for TV, which doesn't navigate to
                                                    // the phone's custom SubtitleStyleScreen and
                                                    // instead renders these as a standard list.
                                                    // The phone screen continues to use the
                                                    // NavigateToSubtitleStyle event path.
                                                    nestedPreferenceGroups =
                                                        listOf(
                                                            PreferenceGroup(
                                                                preferences =
                                                                    listOf(
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_foreground_color,
                                                                            iconDrawableId =
                                                                                R.drawable
                                                                                    .ic_palette,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleForegroundColor,
                                                                            options =
                                                                                R.array
                                                                                    .subtitle_foreground_colors,
                                                                            optionValues =
                                                                                R.array
                                                                                    .subtitle_foreground_color_values,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_background_color,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleBackgroundColor,
                                                                            options =
                                                                                R.array
                                                                                    .subtitle_background_colors,
                                                                            optionValues =
                                                                                R.array
                                                                                    .subtitle_background_color_values,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_edge_type,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleEdgeType,
                                                                            options =
                                                                                R.array
                                                                                    .subtitle_edge_types,
                                                                            optionValues =
                                                                                R.array
                                                                                    .subtitle_edge_type_values,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_edge_color,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleEdgeColor,
                                                                            options =
                                                                                R.array
                                                                                    .subtitle_edge_colors,
                                                                            optionValues =
                                                                                R.array
                                                                                    .subtitle_edge_color_values,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_font_family,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleFontFamily,
                                                                            options =
                                                                                R.array
                                                                                    .subtitle_font_families,
                                                                            optionValues =
                                                                                R.array
                                                                                    .subtitle_font_family_values,
                                                                        ),
                                                                        PreferenceIntInput(
                                                                            nameStringResource =
                                                                                R.string
                                                                                    .subtitle_font_scale,
                                                                            backendPreference =
                                                                                appPreferences
                                                                                    .subtitleFontScale,
                                                                            suffixRes =
                                                                                R.string
                                                                                    .subtitle_font_scale_suffix,
                                                                        ),
                                                                    )
                                                            )
                                                        ),
                                                )
                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource = R.string.pref_player_backend,
                                                    backendPreference = appPreferences.playerBackend,
                                                    options = R.array.player_backends,
                                                    optionValues = R.array.player_backends
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_direct_play_dovi,
                                                    descriptionStringRes =
                                                        R.string.pref_player_direct_play_dovi_summary,
                                                    backendPreference =
                                                        appPreferences.playerDirectPlayDolbyVision,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_tv_match_refresh_rate,
                                                    descriptionStringRes =
                                                        R.string.pref_player_tv_match_refresh_rate_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference =
                                                        appPreferences.playerTvMatchRefreshRate,
                                                ),
                                                PreferenceCategory(
                                                    nameStringResource = R.string.mpv_options,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.NavigateToSettings(
                                                                    listOf(R.string.settings_category_player, it.nameStringResource)
                                                                )
                                                            )
                                                        }
                                                    },
                                                    nestedPreferenceGroups =
                                                        listOf(
                                                            PreferenceGroup(
                                                                preferences =
                                                                    listOf(
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_hwdec,
                                                                            backendPreference =
                                                                                appPreferences.playerMpvHwdec,
                                                                            options = R.array.mpv_hwdec,
                                                                            optionValues = R.array.mpv_hwdec,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_vo,
                                                                            backendPreference = appPreferences.playerMpvVo,
                                                                            options = R.array.mpv_vos,
                                                                            optionValues = R.array.mpv_vos,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_ao,
                                                                            backendPreference = appPreferences.playerMpvAo,
                                                                            options = R.array.mpv_aos,
                                                                            optionValues = R.array.mpv_aos,
                                                                        ),
                                                                    ),
                                                            ),
                                                            PreferenceGroup(
                                                                nameStringResource = R.string.advanced,
                                                                preferences =
                                                                    listOf(
                                                                        PreferenceFileEdit(
                                                                            nameStringResource = R.string.edit_file_title,
                                                                            filePath = "mpv/mpv.conf",
                                                                            onClick = {
                                                                                viewModelScope.launch {
                                                                                    eventsChannel.send(
                                                                                        SettingsEvent.NavigateToSettingsFileEdit(it.filePath)
                                                                                    )
                                                                                }
                                                                            }
                                                                        ),
                                                                        PreferenceFileEdit(
                                                                            nameStringResource = R.string.edit_file_title,
                                                                            filePath = "mpv/input.conf",
                                                                            onClick = {
                                                                                viewModelScope.launch {
                                                                                    eventsChannel.send(
                                                                                        SettingsEvent.NavigateToSettingsFileEdit(it.filePath)
                                                                                    )
                                                                                }
                                                                            }
                                                                        )
                                                                    )
                                                            ),
                                                        )
                                                ),
                                            )
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.gestures,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.player_gestures,
                                                    backendPreference =
                                                        appPreferences.playerGestures,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_vb,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_vb_summary,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesVB,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_seek,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_seek_summary,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesSeek,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_zoom,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_zoom_summary,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesZoom,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.player_gestures_hold,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesHold,
                                                    options = R.array.player_gestures_hold,
                                                    optionValues = R.array.player_gestures_hold_values,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_brightness_remember,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences.playerGestures,
                                                            appPreferences.playerGesturesVB,
                                                        ),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerGesturesBrightnessRemember,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_start_maximized,
                                                    descriptionStringRes =
                                                        R.string.player_start_maximized_summary,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesStartMaximized,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.seeking,
                                        preferences =
                                            listOf(
                                                // CE (#127): TV uses the step selects below.
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.seek_back_increment,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerSeekBackInc,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.seek_forward_increment,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerSeekForwardInc,
                                                    suffixRes = R.string.ms,
                                                ),
                                                // CE (#126): the TV remote's steps and OK key.
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.seek_back_increment,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference =
                                                        appPreferences.playerTvSeekBackInc,
                                                    options = R.array.player_tv_seek_steps,
                                                    optionValues =
                                                        R.array.player_tv_seek_steps_values,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.seek_forward_increment,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference =
                                                        appPreferences.playerTvSeekForwardInc,
                                                    options = R.array.player_tv_seek_steps,
                                                    optionValues =
                                                        R.array.player_tv_seek_steps_values,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_tv_ok_pauses,
                                                    descriptionStringRes =
                                                        R.string.pref_player_tv_ok_pauses_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.TV),
                                                    backendPreference =
                                                        appPreferences.playerTvOkPauses,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_chapter_markers,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_chapter_markers_summary,
                                                    backendPreference =
                                                        appPreferences.playerChapterMarkers,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.media_segments,
                                        preferences =
                                            listOf(
                                                // CE (#271): one action per segment type is the
                                                // only per-type control. The old global switches
                                                // and type lists were migrated into these once
                                                // (AppPreferences.migrateMediaSegmentActions).
                                                mediaSegmentActionSelect(
                                                    R.string.pref_player_media_segments_action_intro,
                                                    appPreferences.playerMediaSegmentsIntroAction,
                                                ),
                                                mediaSegmentActionSelect(
                                                    R.string.pref_player_media_segments_action_outro,
                                                    appPreferences.playerMediaSegmentsOutroAction,
                                                ),
                                                mediaSegmentActionSelect(
                                                    R.string.pref_player_media_segments_action_recap,
                                                    appPreferences.playerMediaSegmentsRecapAction,
                                                ),
                                                mediaSegmentActionSelect(
                                                    R.string
                                                        .pref_player_media_segments_action_preview,
                                                    appPreferences.playerMediaSegmentsPreviewAction,
                                                ),
                                                mediaSegmentActionSelect(
                                                    R.string
                                                        .pref_player_media_segments_action_commercial,
                                                    appPreferences
                                                        .playerMediaSegmentsCommercialAction,
                                                ),
                                                // Shared by phone and TV; TV shows it read-only,
                                                // like its other number settings.
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_duration,
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsSkipButtonDuration,
                                                    suffixRes = R.string.seconds,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_mode,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsAutoSkipMode,
                                                    options = R.array.media_segments_auto_skip,
                                                    optionValues =
                                                        R.array.media_segments_auto_skip_values,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_next_episode_threshold,
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsNextEpisodeThreshold,
                                                    suffixRes = R.string.ms,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.trickplay,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_trickplay,
                                                    descriptionStringRes =
                                                        R.string.pref_player_trickplay_summary,
                                                    // CE (#134): the TV player shows thumbnails
                                                    // while seeking too.
                                                    supportedDeviceTypes =
                                                        listOf(DeviceType.PHONE, DeviceType.TV),
                                                    backendPreference =
                                                        appPreferences.playerTrickplay,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_gestures_seek_trickplay,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_gestures_seek_trickplay_summary,
                                                    dependencies =
                                                        listOf(appPreferences.playerTrickplay),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesSeekTrickplay,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.pref_player_next_episode,
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.pref_player_autoplay_next,
                                                    descriptionStringRes =
                                                        R.string.pref_player_autoplay_next_summary,
                                                    backendPreference =
                                                        appPreferences.playerAutoPlayNext,
                                                    options = R.array.player_autoplay_next,
                                                    optionValues =
                                                        R.array.player_autoplay_next_values,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.pref_player_still_watching,
                                        preferences =
                                            listOf(
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_still_watching_after_episodes,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_still_watching_after_episodes_summary,
                                                    backendPreference =
                                                        appPreferences.stillWatchingAfterEpisodes,
                                                    suffixRes = R.string.episodes,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_still_watching_after_minutes,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_still_watching_after_minutes_summary,
                                                    backendPreference =
                                                        appPreferences.stillWatchingAfterMinutes,
                                                    suffixRes = R.string.minutes,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_still_watching_prompt_timeout,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_still_watching_prompt_timeout_summary,
                                                    backendPreference =
                                                        appPreferences
                                                            .stillWatchingPromptTimeoutSeconds,
                                                    suffixRes = R.string.seconds,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.picture_in_picture,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.picture_in_picture_gesture,
                                                    descriptionStringRes =
                                                        R.string.picture_in_picture_gesture_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerPipGesture,
                                                )
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.pref_player_background_group,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_background_audio,
                                                    descriptionStringRes =
                                                        R.string.pref_player_background_audio_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerBackgroundAudio,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.users,
                            iconDrawableId = R.drawable.ic_user,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToUsers)
                                }
                            },
                        ),
                        PreferenceSwitch(
                            nameStringResource = R.string.pref_ask_user_on_start,
                            descriptionStringRes = R.string.pref_ask_user_on_start_summary,
                            backendPreference = appPreferences.askUserOnStart,
                        ),
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_servers,
                            iconDrawableId = R.drawable.ic_server,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToServers)
                                }
                            },
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.title_download,
                            iconDrawableId = R.drawable.ic_download,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.download_mobile_data,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadOverMobileData,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.download_roaming,
                                                    dependencies = listOf(appPreferences.downloadOverMobileData),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadWhenRoaming,
                                                ),
                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceDynamicSelect(
                                                    nameStringResource = R.string.pref_download_storage_location,
                                                    iconDrawableId = R.drawable.ic_hard_drive,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.downloadStorageIndex,
                                                    dynamicOptions = emptyList(), // filled in loadPreferences
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource = R.string.pref_download_max_concurrent,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.maxConcurrentDownloads,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.pref_download_smart,
                                                    descriptionStringRes = R.string.pref_download_smart_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.smartDownloads,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.pref_download_smart_keep,
                                                    descriptionStringRes =
                                                        R.string.pref_download_smart_keep_summary,
                                                    dependencies =
                                                        listOf(appPreferences.smartDownloads),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.smartDownloadsKeepReady,
                                                    suffixRes = R.string.episodes,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_download_transcode_dovi,
                                                    descriptionStringRes =
                                                        R.string.pref_download_transcode_dovi_summary,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadTranscodeDolbyVision,
                                                ),
                                            )
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_network,
                            iconDrawableId = R.drawable.ic_network,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_request_timeout,
                                                    backendPreference =
                                                        appPreferences.requestTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_connect_timeout,
                                                    backendPreference =
                                                        appPreferences.connectTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_socket_timeout,
                                                    backendPreference =
                                                        appPreferences.socketTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                            )
                                    )
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_cache,
                            iconDrawableId = R.drawable.ic_hard_drive,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            listOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.settings_use_cache_title,
                                                    descriptionStringRes =
                                                        R.string.settings_use_cache_summary,
                                                    backendPreference = appPreferences.imageCache,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.settings_cache_size,
                                                    descriptionStringRes =
                                                        R.string.settings_cache_size_message,
                                                    dependencies =
                                                        listOf(appPreferences.imageCache),
                                                    backendPreference =
                                                        appPreferences.imageCacheSize,
                                                    suffixRes = R.string.mb,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.settings_image_display_size_title,
                                                    descriptionStringRes =
                                                        R.string.settings_image_display_size_summary,
                                                    backendPreference =
                                                        appPreferences.imageDisplaySize,
                                                ),
                                                // CE (#251): the remembered Home rows, apart from
                                                // the image cache, for those who'd rather wait for
                                                // the server than see yesterday's rows first.
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.settings_home_cache_title,
                                                    descriptionStringRes =
                                                        R.string.settings_home_cache_summary,
                                                    backendPreference = appPreferences.homeCache,
                                                ),
                                                PreferenceAction(
                                                    nameStringResource =
                                                        R.string.settings_clear_cache_title,
                                                    descriptionStringRes =
                                                        R.string.settings_clear_cache_summary,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            appCaches.clear()
                                                            eventsChannel.send(
                                                                SettingsEvent.CacheCleared
                                                            )
                                                        }
                                                    },
                                                ),
                                            )
                                    )
                                ),
                        )
                    )
            ),
        ) +
            // Show the Permissions category only if at least one permission applies on this Android
            // version.
            (if (permissionPreferences.any { Build.VERSION.SDK_INT >= it.minSdk }) {
                listOf(permissionsPreferenceGroup)
            } else {
                emptyList()
            }) +
            listOf(
                PreferenceGroup(
                    preferences =
                        listOf(
                            PreferenceCategory(
                                nameStringResource = R.string.about,
                                iconDrawableId = R.drawable.ic_info,
                                onClick = {
                                    viewModelScope.launch {
                                        eventsChannel.send(SettingsEvent.NavigateToAbout)
                                    }
                                },
                            )
                        )
                )
            )

    private fun getStorageOptions(): List<Pair<String?, String>> {
        val dirs = context.getExternalFilesDirs(null)
        return dirs.mapIndexedNotNull { index, dir ->
            if (dir == null) return@mapIndexedNotNull null
            val free = try {
                Formatter.formatFileSize(context, StatFs(dir.path).availableBytes)
            } catch (_: Exception) {
                return@mapIndexedNotNull null
            }
            val label = if (index == 0) {
                context.getString(R.string.pref_download_internal_storage, free)
            } else {
                // index + 1 for 1-based display ("SD card 1", "SD card 2", ...)
                context.getString(R.string.pref_download_sd_card, index + 1, free)
            }
            Pair(index.toString(), label)
        }
    }

    fun loadPreferences(indexes: List<Int> = emptyList(), deviceType: AppVariant = appVariant) {
        viewModelScope.launch {
            var preferences = topLevelPreferences

            // Show preferences based on the name of the parent
            for (index in indexes) {
                // If index is root (Settings) don't search for category
                if (index == R.string.title_settings) {
                    break
                }
                val preference =
                    preferences
                        .flatMap { it.preferences }
                        .filterIsInstance<PreferenceCategory>()
                        .find { it.nameStringResource == index }
                if (preference != null) {
                    preferences = preference.nestedPreferenceGroups
                }
            }

            // Disk syscalls (directories, StatFs): once per load, and off the main thread.
            val storageOptions =
                if (preferences.any { group -> group.preferences.any { it is PreferenceDynamicSelect } }) {
                    withContext(Dispatchers.IO) { getStorageOptions() }
                } else {
                    emptyList()
                }

            // Update all (visible) preferences with there current values
            preferences =
                preferences
                    .map { preferenceGroup ->
                        preferenceGroup.copy(
                            preferences =
                                preferenceGroup.preferences
                                    .filter { it.supportedDeviceTypes.contains(deviceType) }
                                    .filter { preference ->
                                        preference.minSdk?.let { sdk ->
                                            Build.VERSION.SDK_INT >= sdk
                                        } ?: true
                                    }
                                    .map { preference ->
                                        when (preference) {
                                            is PreferenceSwitch -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceSelect -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceMultiSelect -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceIntInput -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceLongInput -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceDynamicSelect -> {
                                                preference.copy(
                                                    enabled = preference.enabled &&
                                                        preference.dependencies.all { appPreferences.getValue(it) },
                                                    dynamicOptions = storageOptions,
                                                    value = appPreferences.getValue(preference.backendPreference),
                                                )
                                            }
                                            else -> preference
                                        }
                                    }
                        )
                    }
                    .filter { it.preferences.isNotEmpty() }

            _state.emit(_state.value.copy(preferenceGroups = preferences))
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnUpdate -> {
                when (action.preference) {
                    is PreferenceSwitch -> {
                        if (
                            action.preference.backendPreference == appPreferences.smartDownloads &&
                                action.preference.value
                        ) {
                            // Smart Downloads only removes what is watched from this moment on.
                            appPreferences.setValue(
                                appPreferences.smartDownloadsSince,
                                System.currentTimeMillis(),
                            )
                        }
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    }
                    is PreferenceSelect ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceMultiSelect ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceIntInput ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            if (action.preference.backendPreference == appPreferences.subtitleFontScale) {
                                action.preference.value.coerceIn(
                                    Constants.SubtitleStyle.FONT_SCALE_MIN,
                                    Constants.SubtitleStyle.FONT_SCALE_MAX,
                                )
                            } else if (
                                action.preference.backendPreference ==
                                    appPreferences.smartDownloadsKeepReady
                            ) {
                                action.preference.value.coerceAtLeast(1)
                            } else {
                                action.preference.value
                            },
                        )
                    is PreferenceLongInput ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceDynamicSelect ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                }
            }
            else -> Unit
        }
    }

    /** A Skip automatically / Ask to skip / Do nothing select for one media segment type. */
    private fun mediaSegmentActionSelect(
        @StringRes nameStringResource: Int,
        backendPreference: PreferenceBackend<String>,
    ) =
        PreferenceSelect(
            nameStringResource = nameStringResource,
            backendPreference = backendPreference,
            options = R.array.media_segments_action,
            optionValues = R.array.media_segments_action_values,
        )
}
