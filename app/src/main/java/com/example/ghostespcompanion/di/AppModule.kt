package com.example.ghostespcompanion.di

import android.content.Context
import com.example.ghostespcompanion.data.LocationHelper
import com.example.ghostespcompanion.data.repository.GhostRepository
import com.example.ghostespcompanion.data.repository.PreferencesRepository
import com.example.ghostespcompanion.data.repository.SettingsManager
import com.example.ghostespcompanion.data.serial.SerialManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing app-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSerialManager(
        @ApplicationContext context: Context
    ): SerialManager {
        return SerialManager(context)
    }
    
    @Provides
    @Singleton
    fun provideGhostRepository(
        serialManager: SerialManager,
        preferencesRepository: PreferencesRepository
    ): GhostRepository {
        return GhostRepository(serialManager, preferencesRepository)
    }
    
    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context
    ): PreferencesRepository {
        return PreferencesRepository(context)
    }
    
    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository
    ): SettingsManager {
        return SettingsManager(context, preferencesRepository)
    }
    
    @Provides
    @Singleton
    fun provideLocationHelper(
        @ApplicationContext context: Context
    ): LocationHelper {
        return LocationHelper(context)
    }
}
