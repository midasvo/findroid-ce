package dev.jdtech.jellyfin.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.about.AboutInfo
import dev.jdtech.jellyfin.about.AboutInfoReader
import dev.jdtech.jellyfin.about.AboutServer
import dev.jdtech.jellyfin.core.AppVariant
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.player.DeviceCapabilityReportBuilder
import dev.jdtech.jellyfin.player.DeviceProfileBuilder
import dev.jdtech.jellyfin.player.ProbedCodecs
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Backs the About screen's "Export device profile" action, and (CE #211) the install, server and
 * device info the TV About screen shows.
 *
 * Lives in `core` because both the device-profile builder (in `data`) and
 * `AppPreferences` (in `settings`) need to be assembled, and `core` is the
 * lowest module that depends on both. The AboutScreen itself stays in
 * `app/phone` and consumes this VM via `hiltViewModel()`.
 *
 * Report assembly + JSON encoding runs on [kotlinx.coroutines.Dispatchers.Default]
 * (see [DeviceCapabilityReportBuilder.buildJson]) so the UI thread is never
 * blocked while a few KB of `kotlinx.serialization` output is produced. The
 * resulting payload is delivered to the UI as a one-shot event.
 */
@HiltViewModel
class AboutViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfileBuilder: DeviceProfileBuilder,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    // A Provider, so the online or offline repository is picked when the info loads.
    private val repository: Provider<JellyfinRepository>,
    val appVariant: AppVariant,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<AboutEvent>()
    val events = eventsChannel.receiveAsFlow()

    fun onAction(action: AboutAction) {
        when (action) {
            AboutAction.OnLoadInfo -> loadInfo()
            is AboutAction.OnExportDeviceProfile -> {
                exportDeviceProfile(
                    findroidVersion = action.findroidVersion,
                    findroidBuildType = action.findroidBuildType,
                    target = action.target,
                )
            }
        }
    }

    private fun loadInfo() {
        if (_state.value.info != null) return
        viewModelScope.launch {
            val info =
                withContext(Dispatchers.Default) {
                    AboutInfoReader.read(context, deviceProfileBuilder)
                }
            _state.update {
                it.copy(info = info, codecs = deviceProfileBuilder.probedCodecsSnapshot())
            }

            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val data = database.getServerWithAddressAndUser(serverId) ?: return@launch
            _state.update {
                it.copy(
                    server =
                        AboutServer(
                            name = data.server.name,
                            address = data.address?.address,
                            userName = data.user?.name,
                            jellyfinVersion = null,
                        )
                )
            }
            val version =
                try {
                    repository.get().getPublicSystemInfo().version
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Could not read the server version")
                    null
                }
            _state.update { it.copy(server = it.server?.copy(jellyfinVersion = version)) }
        }
    }

    private fun exportDeviceProfile(
        findroidVersion: String,
        findroidBuildType: String,
        target: ExportTarget,
    ) {
        // Don't queue a second export while one is already running — the JSON
        // build is cheap but each tap also has UI side effects (chooser /
        // toast) we don't want to fire twice in a row.
        if (_state.value.isExporting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true)
            try {
                val json = DeviceCapabilityReportBuilder.buildJson(
                    context = context,
                    deviceProfileBuilder = deviceProfileBuilder,
                    findroidVersion = findroidVersion,
                    findroidBuildType = findroidBuildType,
                    transcodeDolbyVision = appPreferences.getValue(
                        appPreferences.downloadTranscodeDolbyVision,
                    ),
                    directPlayDolbyVision = appPreferences.getValue(
                        appPreferences.playerDirectPlayDolbyVision,
                    ),
                )
                eventsChannel.send(
                    when (target) {
                        ExportTarget.Share -> AboutEvent.ShareDeviceProfile(json)
                        ExportTarget.Clipboard -> AboutEvent.CopyDeviceProfile(json)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventsChannel.send(AboutEvent.ExportFailed(e))
            } finally {
                _state.value = _state.value.copy(isExporting = false)
            }
        }
    }
}

data class AboutState(
    val isExporting: Boolean = false,
    /** Null until [AboutAction.OnLoadInfo] has read it. */
    val info: AboutInfo? = null,
    val codecs: ProbedCodecs? = null,
    /** Null when there's no current server. */
    val server: AboutServer? = null,
)

sealed interface AboutAction {
    data object OnLoadInfo : AboutAction

    data class OnExportDeviceProfile(
        val findroidVersion: String,
        val findroidBuildType: String,
        val target: ExportTarget,
    ) : AboutAction
}

enum class ExportTarget { Share, Clipboard }

sealed interface AboutEvent {
    data class ShareDeviceProfile(val json: String) : AboutEvent
    data class CopyDeviceProfile(val json: String) : AboutEvent
    data class ExportFailed(val cause: Throwable) : AboutEvent
}
