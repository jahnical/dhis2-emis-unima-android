package org.unima.emis.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.unima.emis.utils.UnimaLogger
import javax.inject.Singleton

/**
 * UNIMA-specific Hilt module for providing UNIMA customizations.
 *
 * This module does NOT replace any base EMIS bindings.
 * It provides additional UNIMA-specific utilities and services.
 */
@Module
@InstallIn(SingletonComponent::class)
object UnimaAppModule {

    @Provides
    @Singleton
    fun providesUnimaLogger(): UnimaLogger = UnimaLogger()
}
