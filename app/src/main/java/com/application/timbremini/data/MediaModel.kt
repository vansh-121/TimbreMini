package com.application.timbremini.data

import android.net.Uri
import java.io.File

/**
 * Metadata and details of a selected audio or video file.
 */
data class MediaItemData(
    val uri: Uri,
    val cachedFile: File,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Long = 0L
) {
    val formattedDuration: String
        get() = formatTimeMs(durationMs)

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups])
        }
}

/**
 * Real-time progress update from FFmpeg during trimming.
 */
data class TrimProgress(
    val percent: Float = 0f,
    val timeProcessedMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val speed: String = "1.0x",
    val bitrate: String = ""
)

/**
 * State of trimming process.
 */
sealed interface TrimState {
    object Idle : TrimState
    data class Preparing(val status: String) : TrimState
    data class Processing(val progress: TrimProgress) : TrimState
    data class Saving(val status: String) : TrimState
    data class Success(val result: TrimResult) : TrimState
    data class Error(val message: String) : TrimState
}

/**
 * Result details after trimming and saving to device storage.
 */
data class TrimResult(
    val outputUri: Uri,
    val outputPath: String,
    val fileSize: Long,
    val durationMs: Long,
    val isVideo: Boolean,
    val localFile: File
) {
    val formattedDuration: String
        get() = formatTimeMs(durationMs)

    val formattedSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(fileSize.toDouble()) / Math.log10(1024.0)).toInt()
            val value = fileSize / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups])
        }
}

/**
 * Formats milliseconds into [HH:]mm:ss or mm:ss.ms
 */
fun formatTimeMs(ms: Long, includeMillis: Boolean = false): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val millis = (ms % 1000) / 100
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return when {
        hours > 0 -> {
            if (includeMillis) {
                String.format("%02d:%02d:%02d.%01d", hours, minutes, seconds, millis)
            } else {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
        }
        else -> {
            if (includeMillis) {
                String.format("%02d:%02d.%01d", minutes, seconds, millis)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Information about a file that cannot be loaded or trimmed.
 */
data class UnsupportedFileInfo(
    val fileName: String,
    val extension: String,
    val reason: String = ""
)

/**
 * Exception thrown when a selected file format is not supported or corrupted.
 */
class UnsupportedFormatException(
    val fileName: String,
    val extension: String,
    message: String
) : Exception(message)
