package com.application.timbremini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.application.timbremini.R
import com.application.timbremini.data.formatTimeMs
import com.application.timbremini.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.trim_range),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Surface(color = Surface2, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = formatTimeMs(trimDurationMs, includeMillis = true),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Amber
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        RangeSlider(
            value = startFloat..endFloat,
            onValueChange = { range ->
                val newStart = range.start.toLong().coerceAtLeast(0L)
                val newEnd = range.endInclusive.toLong().coerceAtMost(totalDurationMs)
                if (newEnd - newStart >= 500L) onRangeChange(newStart, newEnd)
            },
            valueRange = 0f..totalFloat,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = TrackInactive,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            MarkerStepper(
                label = stringResource(R.string.marker_start),
                timeMs = startMs,
                onMinus = { onRangeChange((startMs - 500L).coerceAtLeast(0L), endMs) },
                onPlus = { onRangeChange((startMs + 500L).coerceAtMost(endMs - 500L), endMs) }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(stringResource(R.string.playhead), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatTimeMs(currentPositionMs, includeMillis = true),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }

            MarkerStepper(
                label = stringResource(R.string.marker_end),
                timeMs = endMs,
                onMinus = { onRangeChange(startMs, (endMs - 500L).coerceAtLeast(startMs + 500L)) },
                onPlus = { onRangeChange(startMs, (endMs + 500L).coerceAtMost(totalDurationMs)) }
            )
        }
    }
}

@Composable
private fun MarkerStepper(
    label: String,
    timeMs: Long,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Amber)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
        ) {
            IconButton(onClick = onMinus, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.nudge_back), tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            Text(
                text = formatTimeMs(timeMs, includeMillis = true),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = TextPrimary
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.nudge_fwd), tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
