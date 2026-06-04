package com.example.ui.screens

import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.PlaybackHistory
import com.example.data.model.Video
import com.example.data.model.VideoFolder
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoViewModel
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: VideoViewModel,
    onPlayVideo: (videoPath: String, title: String) -> Unit
) {
    val context = LocalContext.current

    // Dynamically choose permission based on SDK level
    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionString) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        viewModel.updatePermissionState(isGranted)
    }

    LaunchedEffect(hasPermission) {
        viewModel.updatePermissionState(hasPermission)
    }

    // Observe State from architecture flows
    val isLoading by viewModel.isLoading.collectAsState()
    val allVideos by viewModel.videos.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentWatches by viewModel.recentVideos.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    // Screen-level view states
    var activeTab by remember { mutableStateOf(0) } // 0 = All Videos, 1 = Folders
    var searchQuery by remember { mutableStateOf("") }
    var showAboutScreen by remember { mutableStateOf(false) }

    if (showAboutScreen) {
        AboutDeveloperScreen(onBack = { showAboutScreen = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(SleekPurple, SleekDeepPurple)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "NovaPlay Play Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "NovaPlay ",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Ultra",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Light
                                ),
                                color = SleekPurple
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAboutScreen = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About Developer & Creator",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { viewModel.refreshVideos() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Storage",
                                tint = Color.White
                            )
                        }
                        if (recentWatches.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearAllHistory() }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear History",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepBackground,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = DeepBackground
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasPermission) {
                // RENDER GORGEOUS CTA PERMISSIONS PANEL
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(SleekDeepPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Video Library Storage Required",
                            tint = SleekPurple,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Access Local Media",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "NovaPlay Ultra scans and registers offline video containers in local storage folder categories for direct playback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                    Button(
                        onClick = { launcher.launch(permissionString) },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPurple, contentColor = SleekDarkText),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            text = "GRANT STORAGE ACCESS",
                            fontWeight = FontWeight.Bold,
                            color = SleekDarkText
                        )
                    }
                }
            } else {
                // MAIN SCANNABLE HOMEPAGE
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search layout input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Search catalog...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Selector Icon", tint = TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Input Search Query", tint = TextMuted)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SleekPurple,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Determine active items considering folder filter & search queries
                    val filteredVideos = if (selectedFolder != null) {
                        viewModel.getVideosInFolder(selectedFolder!!.folderPath)
                            .filter { it.title.contains(searchQuery, ignoreCase = true) }
                    } else {
                        allVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // 1. Netflix Hero Accent View (If selectedFolder is null)
                        if (selectedFolder == null && recentWatches.isNotEmpty()) {
                            val heroHistory = recentWatches.first()
                            item {
                                HeroHighlightBanner(
                                    history = heroHistory,
                                    onPlay = { onPlayVideo(heroHistory.videoPath, heroHistory.title) }
                                )
                            }
                        }

                        // 2. Continue Watching Horizontal Slider
                        if (selectedFolder == null && recentWatches.size > 1) {
                            item {
                                Text(
                                    text = "Continue Watching",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp)
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Skip first since it is on the premium promo Hero highlight card
                                    items(recentWatches.drop(1)) { watch ->
                                        RecentWatchCard(
                                            watch = watch,
                                            onPlay = { onPlayVideo(watch.videoPath, watch.title) },
                                            onDelete = { viewModel.deleteHistory(watch.videoPath) }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Tab Categories Section (If no folder is drilled into)
                        if (selectedFolder == null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .clickable { activeTab = 0 }
                                            .padding(end = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Videos",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (activeTab == 0) SleekPurple else TextMuted
                                        )
                                        if (activeTab == 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(SleekPurple)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.clickable { activeTab = 1 },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Folders (${folders.size})",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (activeTab == 1) SleekPurple else TextMuted
                                        )
                                        if (activeTab == 1) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(SleekPurple)
                                            )
                                        }
                                    }
                                }
                            }

                            // RENDER SELECTED TAB
                            if (activeTab == 0) {
                                if (filteredVideos.isEmpty()) {
                                    item {
                                        EmptyLibraryLayout(message = if (searchQuery.isNotEmpty()) "No query matches found." else "No offline videos scanned. Copy some media files here to start.")
                                    }
                                } else {
                                    items(filteredVideos) { video ->
                                        VideoListAdapterItem(
                                            video = video,
                                            onClick = { onPlayVideo(video.videoPath, video.title) }
                                        )
                                    }
                                }
                            } else {
                                if (folders.isEmpty()) {
                                    item {
                                        EmptyLibraryLayout(message = "No local group directory paths scanned.")
                                    }
                                } else {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            folders.forEach { folder ->
                                                FolderGridAdapterItem(
                                                    folder = folder,
                                                    onClick = { viewModel.selectFolder(folder) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // DRILLED FOLDER HEADER VIEW
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.selectFolder(null) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(SurfaceCard, RoundedCornerShape(100))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Close Folder Detail View",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = selectedFolder!!.folderName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = selectedFolder!!.folderPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (filteredVideos.isEmpty()) {
                                item {
                                    EmptyLibraryLayout(message = "No nested videos found inside this folder.")
                                }
                            } else {
                                items(filteredVideos) { video ->
                                    VideoListAdapterItem(
                                        video = video,
                                        onClick = { onPlayVideo(video.videoPath, video.title) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = SleekPurple,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    }
}

// ---------------- SUB-COMPONENTS & LAYOUT PARTS ----------------

@Composable
fun HeroHighlightBanner(
    history: PlaybackHistory,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(SleekDeepPurple, DeepBackground)
                )
            )
            .clickable { onPlay() }
    ) {
        // Aesthetic Overlay Matrix Visual Elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(SleekPurple.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .background(SleekLightPurple, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "CONTINUE WATCHING",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = SleekDarkText
                )
            }

            Column {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                val pct = if (history.duration > 0) (history.lastPosition.toFloat() / history.duration.toFloat()) else 0f
                val readableTime = FormatUtils.formatDuration(history.lastPosition)
                Text(
                    text = "Paused at $readableTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Indicator Under Banner
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SleekPurple,
                    trackColor = SleekGreyPurple
                )
            }
        }
    }
}

@Composable
fun RecentWatchCard(
    watch: PlaybackHistory,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SleekDeepPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Watch Icon Link",
                            tint = SleekPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear History Element",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = watch.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))

                val pct = if (watch.duration > 0) watch.lastPosition.toFloat() / watch.duration.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SleekPurple,
                    trackColor = SleekGreyPurple
                )
            }
        }
    }
}

@Composable
fun VideoListAdapterItem(
    video: Video,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SleekGreyPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MovieFilter,
                contentDescription = "Local Video File thumbnail placeholder",
                tint = SleekPurple,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = FormatUtils.formatSize(video.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(100))
                .background(SleekDeepPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Trigger playback",
                tint = SleekPurple,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FolderGridAdapterItem(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SleekDeepPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Directory folder container",
                tint = SleekPurple,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${folder.videoCount} Videos",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate to nested records",
            tint = TextMuted
        )
    }
}

@Composable
fun EmptyLibraryLayout(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(100))
                .background(SleekGreyPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TvOff,
                contentDescription = "Empty state icon indicator",
                tint = SleekPurple,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
