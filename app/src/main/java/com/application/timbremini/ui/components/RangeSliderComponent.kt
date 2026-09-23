package com.application.timbremini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.timbremini.data.formatTimeMs
import com.application.timbremini.ui.theme.*

@Composable
fun RangeSliderComponent(
    totalDurationMs: Long,
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalDurationMs <= 0) return

    val totalFloat = totalDurationMs.toFloat()
    val startFloat = startMs.coerceIn(0L, totalDurationMs).toFloat()
    val endFloat = endMs.coerceIn(startMs, totalDurationMs).toFloat()

    val trimDurationMs = (endMs - startMs).coerceAtLeast(0L)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Section title and Duration badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRIM RANGE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Text(
                        text = "Trimmed: ${formatTimeMs(trimDurationMs, includeMillis = true)}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Range Slider
            RangeSlider(
                value = startFloat..endFloat,
                onValueChange = { range ->
                    val newStart = range.start.toLong().coerceAtLeast(0L)
                    val newEnd = range.endInclusive.toLong().coerceAtMost(totalDurationMs)
                    if (newEnd - newStart >= 500L) { // Min 500ms trim window
                        onRangeChange(newStart, newEnd)
                    }
                },
                valueRange = 0f..totalFloat,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = TrackInactive,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp Badges: Start & End Markers with Precision Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Start Marker Control
                TimeMarkerChip(
                    label = "START",
                    timeMs = startMs,
                    color = NeonCyan,
                    onMinus = {
                        val newStart = (startMs - 500L).coerceAtLeast(0L)
                        onRangeChange(newStart, endMs)
                    },
                    onPlus = {
                        val newStart = (startMs + 500L).coerceAtMost(endMs - 500L)
                        onRangeChange(newStart, endMs)
                    }
                )

                // Current Playhead Position Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "PLAYHEAD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = formatTimeMs(currentPositionMs, includeMillis = true),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                // End Marker Control
                TimeMarkerChip(
                    label = "END",
                    timeMs = endMs,
                    color = NeonPurple,
                    onMinus = {
                        val newEnd = (endMs - 500L).coerceAtLeast(startMs + 500L)
                        onRangeChange(startMs, newEnd)
                    },
                    onPlus = {
                        val newEnd = (endMs + 500L).coerceAtMost(totalDurationMs)
                        onRangeChange(startMs, newEnd)
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeMarkerChip(
    label: String,
    timeMs: Long,
    color: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onMinus,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease $label",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = formatTimeMs(timeMs, includeMillis = true),
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )

                IconButton(
                    onClick = onPlus,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase $label",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
