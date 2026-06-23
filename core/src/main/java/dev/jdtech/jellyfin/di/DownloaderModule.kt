package dev.jdtech.jellyfin.di

import android.app.Application
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.DownloaderImpl
import dev.jdtech.jellyfin.utils.download.MediaDownloadEngine
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {
    @Singleton
    @Provides
    fun provideDownloader(
        application: Application,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepositoryProvider: Provider<JellyfinRepository>,
        appPreferences: AppPreferences,
        workManager: WorkManager,
        engine: MediaDownloadEngine,
    ): Downloader {
        return DownloaderImpl(
            application,
            serverDatabase,
            jellyfinRepositoryProvider,
            appPreferences,
            workManager,
            engine,
        )
    }
}
