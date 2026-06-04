package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.db.PlaybackHistoryDao
import com.example.data.model.PlaybackHistory
import com.example.data.model.Video
import com.example.data.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    val recentHistory: Flow<List<PlaybackHistory>> = playbackHistoryDao.getRecentHistory()

    suspend fun getPlaybackHistory(path: String): PlaybackHistory? {
        return withContext(Dispatchers.IO) {
            playbackHistoryDao.getPlaybackHistory(path)
        }
    }

    suspend fun savePlaybackHistory(path: String, title: String, duration: Long, lastPosition: Long) {
        withContext(Dispatchers.IO) {
            val history = PlaybackHistory(
                videoPath = path,
                title = title,
                duration = duration,
                lastPosition = lastPosition,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
            playbackHistoryDao.savePlaybackHistory(history)
        }
    }

    suspend fun deletePlaybackHistory(path: String) {
        withContext(Dispatchers.IO) {
            playbackHistoryDao.deletePlaybackHistory(path)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            playbackHistoryDao.clearAllHistory()
        }
    }

    suspend fun scanLocalVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<Video>()
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val resolutionCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Video"
                    val videoPath = c.getString(dataCol) ?: ""
                    val duration = c.getLong(durationCol)
                    val size = c.getLong(sizeCol)
                    val resolution = c.getString(resolutionCol)
                    val dateAdded = c.getLong(dateAddedCol)

                    if (videoPath.isNotEmpty()) {
                        val file = File(videoPath)
                        // Make sure file actually exists
                        if (file.exists()) {
                            val parentFile = file.parentFile
                            val folderName = parentFile?.name ?: "Internal Memory"
                            val folderPath = parentFile?.absolutePath ?: ""

                            videoList.add(
                                Video(
                                    id = id,
                                    title = title,
                                    videoPath = videoPath,
                                    duration = duration,
                                    size = size,
                                    folderName = folderName,
                                    folderPath = folderPath,
                                    resolution = resolution,
                                    dateAdded = dateAdded
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videoList
    }

    fun getFolders(videos: List<Video>): List<VideoFolder> {
        val grouped = videos.groupBy { it.folderPath }
        return grouped.map { (folderPath, folderVideos) ->
            val firstVideo = folderVideos.firstOrNull()
            VideoFolder(
                folderName = firstVideo?.folderName ?: "Unknown",
                folderPath = folderPath,
                videoCount = folderVideos.size,
                firstVideoThumbnailPath = firstVideo?.videoPath ?: ""
            )
        }.sortedBy { it.folderName }
    }
}
