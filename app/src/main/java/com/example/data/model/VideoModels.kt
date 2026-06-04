package com.example.data.model

data class Video(
    val id: Long,
    val title: String,
    val videoPath: String,
    val duration: Long,
    val size: Long,
    val folderName: String,
    val folderPath: String,
    val resolution: String?,
    val dateAdded: Long
)

data class VideoFolder(
    val folderName: String,
    val folderPath: String,
    val videoCount: Int,
    val firstVideoThumbnailPath: String = ""
)
