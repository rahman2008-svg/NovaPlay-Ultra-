package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey
    val videoPath: String,
    val title: String,
    val duration: Long,
    val lastPosition: Long,
    val lastPlayedTimestamp: Long
)
