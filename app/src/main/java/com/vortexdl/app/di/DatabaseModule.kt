package com.vortexdl.app.di

import android.content.Context
import androidx.room.Room
import com.vortexdl.app.data.local.DownloadDao
import com.vortexdl.app.data.local.DownloadDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDownloadDatabase(
        @ApplicationContext context: Context,
    ): DownloadDatabase = Room.databaseBuilder(
        context,
        DownloadDatabase::class.java,
        "vortexdl.db",
    )
        .addMigrations(DownloadDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao =
        database.downloadDao()
}
