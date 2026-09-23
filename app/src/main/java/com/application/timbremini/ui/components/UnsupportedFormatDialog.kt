package com.application.timbremini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.application.timbremini.R
import com.application.timbremini.data.UnsupportedFileInfo
import com.application.timbremini.ui.theme.*

@Composable
fun UnsupportedFormatDialog(
    info: UnsupportedFileInfo,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Surface1)
                .border(1.dp, Hairline, RoundedCornerShape(26.dp))
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Amber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = stringResource(R.string.unsupported_format_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Description
                Text(
                    text = stringResource(R.string.unsupported_format_desc, info.fileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (info.reason.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Supported formats card
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Video row
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Videocam,
                                contentDescription = null,
                                tint = Amber,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.supported_video_formats_label),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.supported_video_formats_list),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = Hairline, thickness = 0.5.dp)

                        // Audio row
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Audiotrack,
                                contentDescription = null,
                                tint = Amber,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.supported_audio_formats_label),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.supported_audio_formats_list),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action buttons
                Button(
                    onClick = {
                        onDismiss()
                        onChooseAnother()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.choose_another_file),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
