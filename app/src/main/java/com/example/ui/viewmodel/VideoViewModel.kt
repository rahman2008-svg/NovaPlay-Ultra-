package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.PlaybackHistory
import com.example.data.model.Video
import com.example.data.model.VideoFolder
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VideoViewModel(
    application: Application,
    private val repository: VideoRepository
) : AndroidViewModel(application) {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _folders = MutableStateFlow<List<VideoFolder>>(emptyList())
    val folders: StateFlow<List<VideoFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedFolder: StateFlow<VideoFolder?> = _selectedFolder.asStateFlow()

    val recentVideos: StateFlow<List<PlaybackHistory>> = repository.recentHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _playingVideoPath = MutableStateFlow<String?>(null)
    val playingVideoPath: StateFlow<String?> = _playingVideoPath.asStateFlow()

    private val _playingVideoTitle = MutableStateFlow<String?>(null)
    val playingVideoTitle: StateFlow<String?> = _playingVideoTitle.asStateFlow()

    fun startPlayback(path: String, title: String) {
        _playingVideoPath.value = path
        _playingVideoTitle.value = title
    }

    fun stopPlayback() {
        _playingVideoPath.value = null
        _playingVideoTitle.value = null
    }

    fun updatePermissionState(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            refreshVideos()
        }
    }

    fun refreshVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            val scannedList = repository.scanLocalVideos()
            _videos.value = scannedList
            _folders.value = repository.getFolders(scannedList)
            _isLoading.value = false
        }
    }

    fun selectFolder(folder: VideoFolder?) {
        _selectedFolder.value = folder
    }

    fun getVideosInFolder(folderPath: String): List<Video> {
        return _videos.value.filter { it.folderPath == folderPath }
    }

    suspend fun getPlaybackHistory(path: String): PlaybackHistory? {
        return repository.getPlaybackHistory(path)
    }

    fun saveResumePosition(videoPath: String, title: String, duration: Long, position: Long) {
        viewModelScope.launch {
            repository.savePlaybackHistory(videoPath, title, duration, position)
        }
    }

    fun deleteHistory(videoPath: String) {
        viewModelScope.launch {
            repository.deletePlaybackHistory(videoPath)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    class Factory(
        private val application: Application,
        private val repository: VideoRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VideoViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
