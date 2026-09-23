package com.application.timbremini

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
            val userDarkMode by viewModel.isDarkMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = userDarkMode ?: systemDark

            TimbreMiniTheme(darkTheme = isDark) {
                TimbreMiniApp(
                    viewModel = viewModel,
                    isDark = isDark,
                    onToggleTheme = { viewModel.toggleTheme(isDark) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimbreMiniApp(
    viewModel: TrimViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current

    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val startMs by viewModel.startMs.collectAsState()
    val endMs by viewModel.endMs.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoopTrimActive by viewModel.isLoopTrimActive.collectAsState()
    val trimState by viewModel.trimState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.loadMedia(it) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.loadMedia(it) }
    }
    val anyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadMedia(it) }
    }

    // The SAF pickers need no runtime permission to read. WRITE_EXTERNAL_STORAGE is
    // only required on API <= 28 to insert the export into MediaStore, so that is the
    // only case where we prompt.
    val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val legacyPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    var pendingPickerAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            pendingPickerAction?.invoke()
        } else {
            Toast.makeText(context, context.getString(R.string.err_permission), Toast.LENGTH_LONG).show()
        }
        pendingPickerAction = null
    }

    fun checkAndLaunch(action: () -> Unit) {
        if (!needsLegacyPermission) {
            action()
            return
        }
        val granted = legacyPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            action()
        } else {
            pendingPickerAction = action
            permissionLauncher.launch(legacyPermissions)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    // While editing, the system back button returns to the home screen instead of exiting.
    BackHandler(enabled = selectedMedia != null) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                showChange = selectedMedia != null,
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                onBack = { viewModel.clearSelection() },
                onChange = { checkAndLaunch { anyPicker.launch(arrayOf("audio/*", "video/*")) } }
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
                EmptyState(
                    onPickVideo = { checkAndLaunch { videoPicker.launch("video/*") } },
                    onPickAudio = { checkAndLaunch { audioPicker.launch("audio/*") } },
                    onPickAny = { checkAndLaunch { anyPicker.launch(arrayOf("audio/*", "video/*")) } }
                )
            } else {
                EditorScreen(
                    media = currentMedia,
                    viewModel = viewModel,
                    startMs = startMs,
                    endMs = endMs,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    isLoopTrimActive = isLoopTrimActive
                )
            }

            when (val state = trimState) {
                is TrimState.Preparing -> ProcessingProgressDialog(state.status, null) { viewModel.cancelTrim() }
                is TrimState.Processing -> ProcessingProgressDialog(
                    stringResource(R.string.trimming_ffmpeg), state.progress
                ) { viewModel.cancelTrim() }
                is TrimState.Saving -> ProcessingProgressDialog(state.status, null) { viewModel.cancelTrim() }
                is TrimState.Success -> TrimResultDialog(state.result) { viewModel.dismissResult() }
                is TrimState.Error -> LaunchedEffect(state.message) {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.dismissError()
                }
                TrimState.Idle -> Unit
            }
        }
    }
}
// APPEND_MARKER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    showChange: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onChange: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            if (showChange) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back_home_cd),
                        tint = TextPrimary
                    )
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                        color = TextMuted
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = stringResource(
                        if (isDark) R.string.theme_toggle_light_cd else R.string.theme_toggle_dark_cd
                    ),
                    tint = TextPrimary
                )
            }

            if (showChange) {
                TextButton(
                    onClick = onChange,
                    colors = ButtonDefaults.textButtonColors(contentColor = Amber)
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = stringResource(R.string.change_file_cd), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.change_file), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Ink,
            titleContentColor = TextPrimary,
            actionIconContentColor = Amber
        )
    )
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Amber),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentCut,
            contentDescription = null,
            tint = OnAccent,
            modifier = Modifier.size(20.dp)
        )
    }
}
// APPEND2

@Composable
private fun EditorScreen(
    media: MediaItemData,
    viewModel: TrimViewModel,
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isLoopTrimActive: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PlayerPreviewComponent(
            mediaItem = media,
            player = viewModel.player,
            isPlaying = isPlaying,
            isLoopTrimActive = isLoopTrimActive,
            currentPositionMs = currentPositionMs,
            onPlayPauseToggle = { viewModel.togglePlayPause() },
            onSeekToStart = { viewModel.seekToStart() },
            onSeekToEnd = { viewModel.seekToEnd() },
            onToggleLoopTrim = { viewModel.toggleLoopTrim() }
        )

        DetailsStrip(media = media)

        RangeSliderComponent(
            totalDurationMs = media.durationMs,
            startMs = startMs,
            endMs = endMs,
            currentPositionMs = currentPositionMs,
            onRangeChange = { s, e -> viewModel.updateTrimRange(s, e) }
        )

        Button(
            onClick = { viewModel.startTrim() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.export), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailsStrip(media: MediaItemData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (media.isVideo) Icons.Outlined.Movie else Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = media.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Hairline)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DetailItem(stringResource(R.string.label_duration), media.formattedDuration)
            DetailItem(stringResource(R.string.label_size), media.formattedSize)
            DetailItem(
                label = if (media.isVideo) stringResource(R.string.label_resolution) else stringResource(R.string.label_format),
                value = if (media.isVideo && media.width > 0) "${media.width}×${media.height}" else media.mimeType.substringAfter('/').uppercase()
            )
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
// APPEND3

@Composable
private fun EmptyState(
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickAny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Hairline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCut,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickerTile(
                title = stringResource(R.string.pick_video),
                subtitle = stringResource(R.string.pick_video_formats),
                icon = Icons.Outlined.Videocam,
                modifier = Modifier.weight(1f),
                onClick = onPickVideo
            )
            PickerTile(
                title = stringResource(R.string.pick_audio),
                subtitle = stringResource(R.string.pick_audio_formats),
                icon = Icons.Outlined.GraphicEq,
                modifier = Modifier.weight(1f),
                onClick = onPickAudio
            )
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onPickAny,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.browse_all), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PickerTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}




