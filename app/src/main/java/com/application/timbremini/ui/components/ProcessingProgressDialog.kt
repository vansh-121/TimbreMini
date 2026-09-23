package com.application.timbremini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.application.timbremini.R
import com.application.timbremini.data.TrimProgress
import com.application.timbremini.ui.theme.*

@Composable
fun ProcessingProgressDialog(
    status: String,
    progress: TrimProgress?,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* modal while processing */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Surface1)
                .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.processing_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(Modifier.height(24.dp))

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Amber,
                    trackColor = TrackInactive
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${progress.percent.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Amber
                    )
                    Text(
                        text = progress.speed,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                }
            } else {
                CircularProgressIndicator(
                    color = Amber,
                    modifier = Modifier.size(38.dp),
                    strokeWidth = 3.dp
                )
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
