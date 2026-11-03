package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.settings.presentation.enums.AppVariant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppVariantModule {
    @Provides
    @Singleton
    fun provideAppVariant(): AppVariant = AppVariant.PHONE
}
