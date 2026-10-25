package com.vortexdl.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(HeaderMapConverter::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
