package com.application.timbremini

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.application.timbremini.data.MediaItemData
import com.application.timbremini.data.TrimState
import com.application.timbremini.ui.TrimViewModel
import com.application.timbremini.ui.components.PlayerPreviewComponent
import com.application.timbremini.ui.components.ProcessingProgressDialog
import com.application.timbremini.ui.components.RangeSliderComponent
import com.application.timbremini.ui.components.TrimResultDialog
import com.application.timbremini.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: TrimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimbreMiniTheme {
                TimbreMiniApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimbreMiniApp(viewModel: TrimViewModel) {
    val context = LocalContext.current

    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val startMs by viewModel.startMs.collectAsState()
    val endMs by viewModel.endMs.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoopTrimActive by viewModel.isLoopTrimActive.collectAsState()
    val trimState by viewModel.trimState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // File pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMedia(it) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMedia(it) }
    }

    val anyMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMedia(it) }
    }

    // Permission launcher
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var pendingPickerAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingPickerAction?.invoke()
        } else {
            Toast.makeText(context, "Storage permission is needed to select files", Toast.LENGTH_SHORT).show()
        }
        pendingPickerAction = null
    }

    fun checkAndLaunch(action: () -> Unit) {
        val hasPermissions = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermissions || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            pendingPickerAction = action
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(listOf(NeonCyan, NeonPurple))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Timbre Mini",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Audio & Video Trimmer",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    if (selectedMedia != null) {
                        FilledTonalButton(
                            onClick = {
                                checkAndLaunch { anyMediaPickerLauncher.launch(arrayOf("audio/*", "video/*")) }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkSurfaceVariant,
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Change File",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentMedia = selectedMedia
            if (currentMedia == null) {
                // Empty State: Prompt user to pick audio or video
                EmptyMediaState(
                    onPickVideo = { checkAndLaunch { videoPickerLauncher.launch("video/*") } },
                    onPickAudio = { checkAndLaunch { audioPickerLauncher.launch("audio/*") } },
                    onPickAny = { checkAndLaunch { anyMediaPickerLauncher.launch(arrayOf("audio/*", "video/*")) } }
                )
            } else {
                // Media Editor Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Component with synchronized playback
                    PlayerPreviewComponent(
                        mediaItem = currentMedia,
                        player = viewModel.player,
                        isPlaying = isPlaying,
                        isLoopTrimActive = isLoopTrimActive,
                        currentPositionMs = currentPositionMs,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onSeekToStart = { viewModel.seekToStart() },
                        onSeekToEnd = { viewModel.seekToEnd() },
                        onToggleLoopTrim = { viewModel.toggleLoopTrim() }
                    )

                    // Media Metadata Card
                    MediaDetailsCard(media = currentMedia)

                    // Range Seek Bar Component
                    RangeSliderComponent(
                        totalDurationMs = currentMedia.durationMs,
                        startMs = startMs,
                        endMs = endMs,
                        currentPositionMs = currentPositionMs,
                        onRangeChange = { start, end -> viewModel.updateTrimRange(start, end) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Execute Trimming Action Button
                    Button(
                        onClick = { viewModel.startTrim() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Trim & Save to Device Storage",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Processing Progress Dialog (FFmpeg active)
            when (val state = trimState) {
                is TrimState.Preparing -> {
                    ProcessingProgressDialog(
                        status = state.status,
                        progress = null,
                        onCancel = { viewModel.cancelTrim() }
                    )
                }
                is TrimState.Processing -> {
                    ProcessingProgressDialog(
                        status = "Trimming with FFmpeg...",
                        progress = state.progress,
                        onCancel = { viewModel.cancelTrim() }
                    )
                }
                is TrimState.Saving -> {
                    ProcessingProgressDialog(
                        status = state.status,
                        progress = null,
                        onCancel = { viewModel.cancelTrim() }
                    )
                }
                is TrimState.Success -> {
                    TrimResultDialog(
                        result = state.result,
                        onDismiss = { viewModel.dismissResult() }
                    )
                }
                is TrimState.Error -> {
                    LaunchedEffect(state.message) {
                        snackbarHostState.showSnackbar(state.message)
                        viewModel.dismissError()
                    }
                }
                TrimState.Idle -> { /* Do nothing */ }
            }
        }
    }
}

@Composable
fun EmptyMediaState(
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickAny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Hero Glow Icon
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonCyan.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                .border(2.dp, NeonCyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MovieFilter,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Select Media to Trim",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )

        Text(
            text = "Cut and save any audio or video file with fast FFmpeg processing and Scoped Storage export.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                lineHeight = 22.sp
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons Row: Video & Audio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaPickerCard(
                title = "Select Video",
                subtitle = "MP4, MKV, WebM",
                icon = Icons.Default.Videocam,
                accentColor = NeonCyan,
                modifier = Modifier.weight(1f),
                onClick = onPickVideo
            )

            MediaPickerCard(
                title = "Select Audio",
                subtitle = "MP3, M4A, WAV",
                icon = Icons.Default.Audiotrack,
                accentColor = NeonPurple,
                modifier = Modifier.weight(1f),
                onClick = onPickAudio
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Browse All Files button
        OutlinedButton(
            onClick = onPickAny,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse All Files")
        }
    }
}

@Composable
fun MediaPickerCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun MediaDetailsCard(media: MediaItemData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (media.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (media.isVideo) NeonCyan else NeonPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = media.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(label = "TOTAL DURATION", value = media.formattedDuration)
                DetailItem(label = "FILE SIZE", value = media.formattedSize)
                DetailItem(
                    label = if (media.isVideo) "RESOLUTION" else "MIME TYPE",
                    value = if (media.isVideo && media.width > 0) "${media.width}x${media.height}" else media.mimeType.substringAfter('/')
                )
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}