package dev.jdtech.jellyfin

import android.app.ActivityManager
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.JellyfinImageSizeInterceptor
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class BaseApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()

        // Issue #52: correct an "aaudio" value left in preferences by the old default.
        appPreferences.migrateMpvAudioOutput()
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalTime::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(
                    JellyfinImageSizeInterceptor {
                        appPreferences.getValue(appPreferences.imageDisplaySize)
                    }
                )
                add(OkHttpNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() }))
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                val memoryClassMb = getSystemService(ActivityManager::class.java).memoryClass
                MemoryCache.Builder().maxSizeBytes(imageMemoryCacheBytes(memoryClassMb)).build()
            }
            .diskCachePolicy(
                if (appPreferences.getValue(appPreferences.imageCache)) CachePolicy.ENABLED
                else CachePolicy.DISABLED
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(
                        appPreferences.getValue(appPreferences.imageCacheSize) * 1024L * 1024
                    )
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

/**
 * The TV image memory cache: [MEMORY_CACHE_PERCENT] of the app's memory, at most
 * [MEMORY_CACHE_MAX_MB]. More than Coil's default, so the Home hero's prefetched backdrops (#155),
 * about 4 MB each at 1080p, fit next to the posters on screen without pushing them out. The cap keeps
 * a box with a large heap from holding on to more than it needs.
 */
internal fun imageMemoryCacheBytes(memoryClassMb: Int): Long =
    minOf(memoryClassMb * MEMORY_CACHE_PERCENT / 100, MEMORY_CACHE_MAX_MB) * 1024L * 1024

private const val MEMORY_CACHE_PERCENT = 30
private const val MEMORY_CACHE_MAX_MB = 96
