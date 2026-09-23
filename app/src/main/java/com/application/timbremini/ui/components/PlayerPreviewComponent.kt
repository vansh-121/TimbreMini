package com.application.timbremini.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.application.timbremini.data.MediaItemData
import com.application.timbremini.data.formatTimeMs
import com.application.timbremini.ui.theme.*

@Composable
fun PlayerPreviewComponent(
    mediaItem: MediaItemData,
    player: Player?,
    isPlaying: Boolean,
    isLoopTrimActive: Boolean,
    currentPositionMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeekToStart: () -> Unit,
    onSeekToEnd: () -> Unit,
    onToggleLoopTrim: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media Display Area (Video View or Audio Visualizer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (mediaItem.isVideo && player != null) {
                    // Video PlayerView
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this.player = player
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Audio Visualizer Card
                    AudioVisualizerPlaceholder(
                        isPlaying = isPlaying,
                        mediaName = mediaItem.name
                    )
                }

                // File type badge in top-left corner
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (mediaItem.isVideo) Icons.Default.Videocam else Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (mediaItem.isVideo) NeonCyan else NeonPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (mediaItem.isVideo) "VIDEO" else "AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            // Playback Control Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current playhead vs total duration
                    Text(
                        text = "${formatTimeMs(currentPositionMs)} / ${mediaItem.formattedDuration}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    // Loop Trim Range Toggle Chip
                    FilterChip(
                        selected = isLoopTrimActive,
                        onClick = onToggleLoopTrim,
                        label = {
                            Text(
                                text = "Loop Trim",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isLoopTrimActive) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Loop Trim Range",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            selectedLeadingIconColor = NeonCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Media Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Jump to Start Marker
                    FilledTonalIconButton(
                        onClick = onSeekToStart,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Jump to Trim Start"
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Play/Pause Main Button
                    FilledIconButton(
                        onClick = onPlayPauseToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkBackground
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Jump to End Marker
                    FilledTonalIconButton(
                        onClick = onSeekToEnd,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Jump to Trim End"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioVisualizerPlaceholder(
    isPlaying: Boolean,
    mediaName: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val barCount = 18

    val heights = (0 until barCount).map { index ->
        if (isPlaying) {
            val duration = 400 + (index * 60) % 500
            infiniteTransition.animateFloat(
                initialValue = 12f,
                targetValue = 65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            ).value
        } else {
            12f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Animated sound wave bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(70.dp)
        ) {
            heights.forEachIndexed { i, height ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonCyan, NeonPurple)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = mediaName,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
