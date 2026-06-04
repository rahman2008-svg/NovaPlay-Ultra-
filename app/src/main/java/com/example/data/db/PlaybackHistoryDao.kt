package com.example.data.db

import androidx.room.*
import com.example.data.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTimestamp DESC")
    fun getRecentHistory(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE videoPath = :path LIMIT 1")
    suspend fun getPlaybackHistory(path: String): PlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE videoPath = :path")
    suspend fun deletePlaybackHistory(path: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}
