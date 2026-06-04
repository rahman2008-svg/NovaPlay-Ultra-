package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.VideoRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core data layer construction
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(applicationContext, database.playbackHistoryDao)
        val factory = VideoViewModel.Factory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[VideoViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val playingVideoPath by viewModel.playingVideoPath.collectAsState()
                val playingVideoTitle by viewModel.playingVideoTitle.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (playingVideoPath != null && playingVideoTitle != null) {
                        PlayerScreen(
                            videoPath = playingVideoPath!!,
                            videoTitle = playingVideoTitle!!,
                            viewModel = viewModel,
                            onBack = {
                                viewModel.stopPlayback()
                                // Re-trigger local scans when coming back to update history layout offsets
                                viewModel.refreshVideos()
                            }
                        )
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            onPlayVideo = { path, title ->
                                viewModel.startPlayback(path, title)
                            }
                        )
                    }
                }
            }
        }
    }
}
