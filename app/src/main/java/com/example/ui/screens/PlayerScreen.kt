package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoViewModel
import com.example.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    videoPath: String,
    videoTitle: String,
    viewModel: VideoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Retrieve initial resume position from database
    var initialPosition by remember { mutableStateOf(0L) }
    var resumeLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(videoPath) {
        val history = viewModel.getPlaybackHistory(videoPath)
        initialPosition = history?.lastPosition ?: 0L
        resumeLoaded = true
    }

    if (!resumeLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CinemaRed)
        }
        return
    }

    // Capture initial system brightness and volume settings to manage gestures
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // State for player settings
    var isPlaying by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleName by remember { mutableStateOf<String?>(null) }

    // Timing state variables
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferPercentage by remember { mutableStateOf(0) }

    // Init ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Subtitle picker activity launcher
    val subtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            subtitleUri = uri
            subtitleName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom.srt"

            // Re-apply media source configuration supporting selected subtitles
            val currentPos = exoPlayer.currentPosition
            val isUserPlaying = exoPlayer.isPlaying

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoPath))
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()

            exoPlayer.setMediaItem(mediaItem, currentPos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = isUserPlaying
        }
    }

    // Layout configuration and lifecycle callbacks
    DisposableEffect(videoPath) {
        // Enforce landscape viewing for fullscreen cine pleasure
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Configure system UI full screen (hide status/navigation panels)
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        // Base Media item
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(videoPath))
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (initialPosition > 0L) {
            exoPlayer.seekTo(initialPosition)
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            // Persist watching history offset on exit
            val finalPosition = exoPlayer.currentPosition
            val totalDuration = exoPlayer.duration
            if (totalDuration > 0) {
                viewModel.saveResumePosition(
                    videoPath = videoPath,
                    title = videoTitle,
                    duration = totalDuration,
                    position = finalPosition
                )
            }

            exoPlayer.removeListener(listener)
            exoPlayer.release()

            // Restore screen alignment and system bars
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Tick current duration offsets
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            bufferPercentage = exoPlayer.bufferedPercentage
            delay(500)
        }
    }

    // Auto-hiding HUD logic
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3500)
            isControlsVisible = false
        }
    }

    // Gesture control HUD overlay values
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingSeek by remember { mutableStateOf(false) }

    var gestureVolumeValue by remember { mutableStateOf(0) }
    var gestureBrightnessValue by remember { mutableStateOf(0f) }
    var gestureSeekDeltaSeconds by remember { mutableStateOf(0L) }
    var gestureSeekTargetValue by remember { mutableStateOf(0L) }

    // Screen dimensions (pixels) for gesture multiplier calculations
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var currentBrightnessOnDragStart by remember { mutableStateOf(0.5f) }
    var currentVolumeOnDragStart by remember { mutableStateOf(0) }
    var currentPositionOnDragStart by remember { mutableStateOf(0L) }

    // Handle speed options
    var showSpeedDialog by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) {
                    // Tap on screen unlocks controls or shows lock indicator
                    detectDragGestures(
                        onDrag = { _, _ -> },
                        onDragStart = { isControlsVisible = true }
                    )
                    return@pointerInput
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        isControlsVisible = true
                        currentPositionOnDragStart = exoPlayer.currentPosition

                        // Determine initial brightness of activity
                        val lp = activity?.window?.attributes
                        currentBrightnessOnDragStart = if (lp != null && lp.screenBrightness >= 0f) {
                            lp.screenBrightness
                        } else {
                            0.5f // Default anchor
                        }

                        // Determine current system volume
                        currentVolumeOnDragStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    },
                    onDragEnd = {
                        isDraggingVolume = false
                        isDraggingBrightness = false
                        if (isDraggingSeek) {
                            exoPlayer.seekTo(gestureSeekTargetValue)
                            isDraggingSeek = false
                        }
                    },
                    onDragCancel = {
                        isDraggingVolume = false
                        isDraggingBrightness = false
                        isDraggingSeek = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        val isHorizontal = abs(dragAmount.x) > abs(dragAmount.y)

                        if (!isDraggingVolume && !isDraggingBrightness && !isDraggingSeek) {
                            // First move establishes drag type
                            if (isHorizontal) {
                                isDraggingSeek = true
                            } else {
                                // Split screen on middle
                                val startX = change.position.x - dragAmount.x
                                if (startX < screenWidthPx / 2) {
                                    isDraggingBrightness = true
                                } else {
                                    isDraggingVolume = true
                                }
                            }
                        }

                        // Execute values
                        if (isDraggingSeek) {
                            // Swipe left/right = seek
                            val secondsDelta = (dragAmount.x * 250).toLong() // scale speed
                            gestureSeekTargetValue = (exoPlayer.currentPosition + secondsDelta)
                                .coerceIn(0L, duration)
                            gestureSeekDeltaSeconds = (gestureSeekTargetValue - currentPositionOnDragStart) / 1000L
                            currentPosition = gestureSeekTargetValue
                        } else if (isDraggingBrightness) {
                            // Left side swipe up/down = brightness
                            // dragAmount.y is negative when moving UP
                            val rawDelta = -dragAmount.y / screenHeightPx
                            val finalBright = (currentBrightnessOnDragStart + rawDelta).coerceIn(0.01f, 1f)
                            currentBrightnessOnDragStart = finalBright
                            gestureBrightnessValue = finalBright

                            val lp = activity?.window?.attributes
                            if (lp != null) {
                                lp.screenBrightness = finalBright
                                activity.window.attributes = lp
                            }
                        } else if (isDraggingVolume) {
                            // Right side swipe up/down = volume
                            val stepDelta = if (-dragAmount.y > 0) 1 else -1
                            if (abs(dragAmount.y) > 25f) { // threshold multiplier
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val updatedVol = (currentVol + stepDelta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, updatedVol, 0)
                                gestureVolumeValue = updatedVol
                            }
                        }
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible
            }
    ) {
        // Player Surface Rendering
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use clean custom Compose Controls
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = exoPlayer
            }
        )

        // GESTURE SLIDER OVERLAYS (Netflix inspired minimal glass indicators)
        Box(modifier = Modifier.fillMaxSize()) {
            // Seek Indicator Overlay
            if (isDraggingSeek) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (gestureSeekDeltaSeconds >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = "Seek Action Indicator",
                            tint = CinemaRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${if (gestureSeekDeltaSeconds >= 0) "+" else ""}${gestureSeekDeltaSeconds}s",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "${FormatUtils.formatDuration(gestureSeekTargetValue)} / ${FormatUtils.formatDuration(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // Brightness Overlay (Left Side)
            if (isDraggingBrightness) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Brightness Gesture Overlay",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .width(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(gestureBrightnessValue)
                                    .background(Color.White)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(gestureBrightnessValue * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Volume Overlay (Right Side)
            if (isDraggingVolume) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (gestureVolumeValue == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Volume Gesture Overlay",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .width(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(if (maxVolume > 0) gestureVolumeValue.toFloat() / maxVolume else 0f)
                                    .background(CinemaRed)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$gestureVolumeValue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // FULL SCREEN CONTROLS HUD (Fades dynamically)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                if (isLocked) {
                    // Show ONLY Unlock button when screen interactive features are locked
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(CinemaRed, RoundedCornerShape(100))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock Input Gesture Controls",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    // --- TOP CONTROL BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Return to Library",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = videoTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                if (subtitleName != null) {
                                    Text(
                                        text = "Subtitles: $subtitleName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }

                        // Right-aligned settings (Subtitle picked & speed dialog shortcuts)
                        Row {
                            // Subtitle Picker button
                            Button(
                                onClick = { subtitleLauncher.launch("application/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Load Subtitles",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SRT", style = MaterialTheme.typography.labelSmall)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Playback Speed adjustment button
                            Button(
                                onClick = { showSpeedDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed Controls",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${currentSpeed}x", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // --- CENTER CONTROL ACTIONS (Seek & Lock & Play/Pause) ---
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.7f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gesture Lock button
                        IconButton(
                            onClick = { isLocked = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100))
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls Gesture Mode",
                                tint = Color.White
                            )
                        }

                        // Seek Left 10 seconds
                        IconButton(
                            onClick = {
                                val jump = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(jump)
                                currentPosition = jump
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind Ten Seconds",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause circular click
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(CinemaRed, RoundedCornerShape(100))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Pause Toggle Action",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Forward 10 seconds
                        IconButton(
                            onClick = {
                                val jump = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                exoPlayer.seekTo(jump)
                                currentPosition = jump
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward Ten Seconds",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Stop / Reset to Home button
                        IconButton(
                            onClick = { onBack() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Video Playback",
                                tint = Color.White
                            )
                        }
                    }

                    // --- BOTTOM SEEK & TIMELINE INTERFACE ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        // Slider and timing indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(currentPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )

                            // Media Slider Timeline
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() else 0f,
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { targetPos ->
                                    currentPosition = targetPos.toLong()
                                    exoPlayer.seekTo(currentPosition)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = CinemaRed,
                                    inactiveTrackColor = Color.DarkGray,
                                    thumbColor = CinemaRed
                                )
                            )

                            Text(
                                text = FormatUtils.formatDuration(duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // SPEED PICKER SELECTOR DIALOG
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    text = "Select Playback Speed",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            },
            containerColor = SurfaceDark,
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSpeed = speed
                                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (speed == currentSpeed) CinemaRed else Color.White
                            )
                            if (speed == currentSpeed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Currently selected",
                                    tint = CinemaRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = CinemaRed)
                }
            }
        )
    }
}
