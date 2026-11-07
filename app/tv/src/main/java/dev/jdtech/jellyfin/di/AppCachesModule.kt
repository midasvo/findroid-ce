package dev.jdtech.jellyfin.di

import android.content.Context
import coil3.SingletonImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.film.presentation.home.HomeCache
import dev.jdtech.jellyfin.settings.domain.AppCaches
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The caches the settings' Clear cache row empties: Coil's memory and disk caches, and Home's rows. */
@Module
@InstallIn(SingletonComponent::class)
object AppCachesModule {
    @Provides
    @Singleton
    fun provideAppCaches(@ApplicationContext context: Context, homeCache: HomeCache): AppCaches =
        object : AppCaches {
            override suspend fun clear() {
                withContext(Dispatchers.IO) {
                    SingletonImageLoader.get(context).apply {
                        memoryCache?.clear()
                        diskCache?.clear()
                    }
                    homeCache.clear()
                }
            }
        }
}
