package com.application.timbremini.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.application.timbremini.R
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        // Media stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (mediaItem.isVideo && player != null) {
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
                    update = { view -> if (view.player != player) view.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AudioVisualizer(isPlaying = isPlaying, mediaName = mediaItem.name)
            }

            // Type badge
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
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
                        imageVector = if (mediaItem.isVideo) Icons.Outlined.Videocam else Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (mediaItem.isVideo) stringResource(R.string.badge_video) else stringResource(R.string.badge_audio),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }

        // Controls
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTimeMs(currentPositionMs)} / ${mediaItem.formattedDuration}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
                FilterChip(
                    selected = isLoopTrimActive,
                    onClick = onToggleLoopTrim,
                    label = {
                        Text(stringResource(R.string.loop_range), style = MaterialTheme.typography.labelLarge)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Repeat,
                            contentDescription = stringResource(R.string.loop_range_cd),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Surface2,
                        labelColor = TextSecondary,
                        iconColor = TextSecondary,
                        selectedContainerColor = Amber.copy(alpha = 0.14f),
                        selectedLabelColor = Amber,
                        selectedLeadingIconColor = Amber
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onSeekToStart,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Surface2, contentColor = TextPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.seek_start_cd))
                }

                Spacer(Modifier.width(22.dp))

                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Amber, contentColor = OnAccent
                    ),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause_cd) else stringResource(R.string.play_cd),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.width(22.dp))

                FilledTonalIconButton(
                    onClick = onSeekToEnd,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Surface2, contentColor = TextPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.seek_end_cd))
                }
            }
        }
    }
}

@Composable
private fun AudioVisualizer(isPlaying: Boolean, mediaName: String) {
    val transition = rememberInfiniteTransition(label = "bars")
    val barCount = 24

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(64.dp)
        ) {
            (0 until barCount).forEach { index ->
                val height = if (isPlaying) {
                    val duration = 360 + (index * 47) % 460
                    transition.animateFloat(
                        initialValue = 8f,
                        targetValue = 58f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$index"
                    ).value
                } else {
                    // A calm, static waveform-like silhouette when paused.
                    10f + (index % 5) * 6f
                }
                // Fade the accent toward the edges so the cluster reads as one shape.
                val edge = 1f - (kotlin.math.abs(index - barCount / 2f) / (barCount / 2f)) * 0.5f
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Amber.copy(alpha = 0.35f + 0.45f * edge))
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = mediaName,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
