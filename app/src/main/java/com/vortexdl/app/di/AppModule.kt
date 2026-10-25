package com.vortexdl.app.di

import com.vortexdl.app.data.repository.DownloadRepositoryImpl
import com.vortexdl.app.data.repository.MediaRepositoryImpl
import com.vortexdl.app.domain.repository.DownloadRepository
import com.vortexdl.app.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(implementation: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(implementation: DownloadRepositoryImpl): DownloadRepository
}
