package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the OkHttpClient used exclusively by [MediaDownloadEngine].
 * Differs from the Jellyfin API client: callTimeout=0 (downloads are long-running),
 * longer read-timeout so a 60 s stall surfaces as IOException rather than hanging forever.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Singleton
    @Provides
    @DownloadHttpClient
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.MILLISECONDS) // no overall cap — downloads are long
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
}
