package com.vortexdl.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAt ASC")
    suspend fun getActiveDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getDownloadsByStatus(status: String): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBytesPerSecond = :speed WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long, speed: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, completedAt = :completedAt, filePath = COALESCE(:filePath, filePath), speedBytesPerSecond = 0 WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        errorMessage: String? = null,
        completedAt: Long? = null,
        filePath: String? = null,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
