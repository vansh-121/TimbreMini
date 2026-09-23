package com.application.timbremini.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStorageManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Resolves metadata and copies the media file to the app's cache directory
     * so FFmpeg C-layer has a direct, reliable seekable file path.
     */
    suspend fun prepareMediaItem(uri: Uri): Result<MediaItemData> = withContext(Dispatchers.IO) {
        try {
            val (displayName, size) = queryFileInfo(uri)
            val mimeType = resolveMimeType(uri, displayName)
            val isVideo = mimeType.startsWith("video/") || displayName.endsWith(".mp4", ignoreCase = true)
                    || displayName.endsWith(".mkv", ignoreCase = true)
                    || displayName.endsWith(".webm", ignoreCase = true)
                    || displayName.endsWith(".mov", ignoreCase = true)

            // Determine file extension
            val extension = displayName.substringAfterLast('.', if (isVideo) "mp4" else "mp3")
            val cacheFile = File(context.cacheDir, "input_source_${System.currentTimeMillis()}.$extension")

            // Copy input stream to cache
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Unable to read selected media file"))

            // Extract audio/video metadata using MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var width = 0
            var height = 0
            var bitrate = 0L

            try {
                retriever.setDataSource(cacheFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                if (isVideo) {
                    val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = widthStr?.toIntOrNull() ?: 0
                    height = heightStr?.toIntOrNull() ?: 0
                }

                val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitrate = bitrateStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve full metadata: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }

            val finalSize = if (size > 0) size else cacheFile.length()

            Result.success(
                MediaItemData(
                    uri = uri,
                    cachedFile = cacheFile,
                    name = displayName,
                    durationMs = durationMs,
                    sizeBytes = finalSize,
                    mimeType = mimeType,
                    isVideo = isVideo,
                    width = width,
                    height = height,
                    bitrate = bitrate
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing media item", e)
            Result.failure(e)
        }
    }

    /**
     * Saves the trimmed file to public device storage via MediaStore (Scoped Storage compliant).
     * Videos are saved to Movies/TimbreMini, Audio to Music/TimbreMini.
     */
    suspend fun saveTrimmedFileToDeviceStorage(
        trimmedFile: File,
        originalName: String,
        isVideo: Boolean,
        durationMs: Long
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val originalBaseName = originalName.substringBeforeLast('.')
            val extension = trimmedFile.extension.ifEmpty { if (isVideo) "mp4" else "mp3" }
            val outputFileName = "Timbre_${originalBaseName}_$timestamp.$extension"

            val mimeType = if (isVideo) {
                when (extension.lowercase()) {
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    else -> "video/mp4"
                }
            } else {
                when (extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a", "aac" -> "audio/mp4"
                    "wav" -> "audio/wav"
                    "ogg" -> "audio/ogg"
                    else -> "audio/mpeg"
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativeDir = if (isVideo) {
                        Environment.DIRECTORY_MOVIES + "/TimbreMini"
                    } else {
                        Environment.DIRECTORY_MUSIC + "/TimbreMini"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                if (isVideo) {
                    put(MediaStore.Video.Media.DURATION, durationMs)
                } else {
                    put(MediaStore.Audio.Media.DURATION, durationMs)
                }
            }

            val collectionUri = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            }

            val insertedUri = contentResolver.insert(collectionUri, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            // Write content
            contentResolver.openOutputStream(insertedUri)?.use { outputStream ->
                FileInputStream(trimmedFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            // Finish pending state on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(insertedUri, completeValues, null, null)
            }

            val folderName = if (isVideo) "Movies/TimbreMini" else "Music/TimbreMini"
            val displayPath = "$folderName/$outputFileName"

            Result.success(Pair(insertedUri, displayPath))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving trimmed file to device storage", e)
            Result.failure(e)
        }
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = "media_${System.currentTimeMillis()}"
        var size = 0L

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = File(path)
                name = file.name
                size = file.length()
            }
        }
        return Pair(name, size)
    }

    private fun resolveMimeType(uri: Uri, displayName: String): String {
        val type = contentResolver.getType(uri)
        if (!type.isNullOrEmpty() && type != "application/octet-stream") {
            return type
        }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> "video/mp4"
        }
    }

    fun cleanCache() {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("input_source_") || file.name.startsWith("trimmed_temp_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning cache: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MediaStorageManager"
    }
}
