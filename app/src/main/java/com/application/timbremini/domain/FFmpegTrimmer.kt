package com.application.timbremini.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.application.timbremini.data.TrimProgress
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class FFmpegTrimmer(private val context: Context) {

    private var activeSession: FFmpegSession? = null

    /** Most recently completed session; used for cancellation detection and error diagnostics. */
    private var lastSession: FFmpegSession? = null
    private var lastReturnCode: ReturnCode? = null

    /**
     * Trims media from [startMs] to [endMs] in the background.
     *
     * @param inputFile Source media file
     * @param startMs Start timestamp in milliseconds
     * @param endMs End timestamp in milliseconds
     * @param isVideo True if media is video, false for audio
     * @param onProgress Callback receiving real-time progress updates
     * @return Result containing the trimmed File in cache
     */
    suspend fun trim(
        inputFile: File,
        startMs: Long,
        endMs: Long,
        isVideo: Boolean,
        onProgress: (TrimProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDurationMs = (endMs - startMs).coerceAtLeast(100L)
        val extension = inputFile.extension.ifEmpty { if (isVideo) "mp4" else "mp3" }
        val outputFile = File(context.cacheDir, "trimmed_temp_${System.currentTimeMillis()}.$extension")

        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Format timestamps in seconds with millisecond precision (e.g. 12.345)
        val startSec = String.format(Locale.US, "%.3f", startMs / 1000.0)
        val targetDurationSec = String.format(Locale.US, "%.3f", targetDurationMs / 1000.0)

        // Strategy 1: Fast Lossless Stream Copy (-c copy)
        // Using -ss before -i with -t guarantees precise seek and duration
        // -avoid_negative_ts make_zero ensures monotonic timestamps starting at zero
        val copyCommand = "-y -ss $startSec -i \"${inputFile.absolutePath}\" -t $targetDurationSec -c copy -avoid_negative_ts make_zero \"${outputFile.absolutePath}\""
        Log.d(TAG, "Executing FFmpeg Stream Copy command: $copyCommand")

        val copyResult = executeSession(copyCommand, targetDurationMs, onProgress)

        if (copyResult && outputFile.exists() && outputFile.length() > 0) {
            // For video: Stream copy can only cut on keyframes, so the start snaps to the nearest
            // preceding keyframe. If the GOP is large, the clip can be inflated well beyond
            // the requested range (e.g. 1s selection exporting as ~9s). We verify duration
            // and fall through to frame-accurate re-encode if it snapped to an earlier keyframe.
            // For audio: Audio packets are 10-30ms, but streaming containers (like Ogg Opus / MP3 Xing)
            // have container page sync or metadata jitter of ~0.5s - 1.5s. Audio stream copy
            // is fast and lossless, so we use a generous tolerance for audio.
            val actualMs = probeDurationMs(outputFile)
            val tolerance = if (isVideo) {
                maxOf(400L, targetDurationMs / 20)
            } else {
                maxOf(1500L, targetDurationMs / 10)
            }

            val isValidDuration = if (actualMs > 0) {
                kotlin.math.abs(actualMs - targetDurationMs) <= tolerance
            } else {
                // If retriever couldn't probe duration (common with some audio formats like Opus on certain ROMs),
                // accept stream copy for audio if file was created successfully with valid size
                !isVideo && outputFile.length() > 512L
            }

            if (isValidDuration) {
                Log.d(TAG, "Stream copy accepted. Target=${targetDurationMs}ms actual=${actualMs}ms size=${outputFile.length()}")
                return@withContext Result.success(outputFile)
            }
            Log.w(TAG, "Stream copy rejected (target=${targetDurationMs}ms actual=${actualMs}ms tolerance=${tolerance}ms). Re-encoding for accuracy.")
        }

        // If stream copy was cancelled by the user, abort instead of re-encoding.
        if (ReturnCode.isCancel(lastReturnCode)) {
            return@withContext Result.failure(Exception("Trimming cancelled by user"))
        }

        // Strategy 2: Fallback to Precise Re-encoding if stream copy fails or was rejected
        Log.w(TAG, "Falling back to precise re-encoding...")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Re-encoded H.264/AAC video must live in a compatible container (.mp4).
        // For audio, container must match the audio encoder (e.g., Opus in .opus, MP3 in .mp3, AAC in .m4a).
        val reencodeFile: File
        val reencodeCommand: String

        if (isVideo) {
            reencodeFile = File(context.cacheDir, "trimmed_temp_${System.currentTimeMillis()}.mp4")
            reencodeCommand = "-y -ss $startSec -i \"${inputFile.absolutePath}\" -t $targetDurationSec -c:v libopenh264 -b:v 6M -pix_fmt yuv420p -c:a aac -b:a 192k \"${reencodeFile.absolutePath}\""
        } else {
            val (codecArgs, targetExt) = getAudioCodecAndExtension(extension)
            reencodeFile = File(context.cacheDir, "trimmed_temp_${System.currentTimeMillis()}.$targetExt")
            reencodeCommand = "-y -ss $startSec -i \"${inputFile.absolutePath}\" -t $targetDurationSec $codecArgs \"${reencodeFile.absolutePath}\""
        }

        Log.d(TAG, "Executing FFmpeg Re-encode command: $reencodeCommand")
        val reencodeResult = executeSession(reencodeCommand, targetDurationMs, onProgress)

        // Strategy 3: Graceful fallback for audio if container-specific encoder is unavailable
        if (!reencodeResult && !isVideo && !ReturnCode.isCancel(lastReturnCode)) {
            Log.w(TAG, "Primary audio re-encode failed. Attempting universal AAC (.m4a) fallback...")
            val fallbackFile = File(context.cacheDir, "trimmed_temp_${System.currentTimeMillis()}.m4a")
            val fallbackCommand = "-y -ss $startSec -i \"${inputFile.absolutePath}\" -t $targetDurationSec -c:a aac -b:a 192k \"${fallbackFile.absolutePath}\""
            val fallbackSuccess = executeSession(fallbackCommand, targetDurationMs, onProgress)
            if (fallbackSuccess && fallbackFile.exists() && fallbackFile.length() > 0) {
                Log.d(TAG, "Universal audio fallback succeeded.")
                return@withContext Result.success(fallbackFile)
            }
        }

        if (reencodeResult && reencodeFile.exists() && reencodeFile.length() > 0) {
            val probedDuration = probeDurationMs(reencodeFile)
            if (probedDuration > 0 || (!isVideo && reencodeFile.length() > 512L)) {
                Log.d(TAG, "Re-encode succeeded. Output size: ${reencodeFile.length()} bytes")
                return@withContext Result.success(reencodeFile)
            }
        }

        val failureMessage = lastSession?.failStackTrace
            ?: lastSession?.allLogsAsString
            ?: "FFmpeg execution failed with return code: $lastReturnCode"
        Log.e(TAG, "FFmpeg trimming failed: $failureMessage")
        Result.failure(Exception("Trimming failed: $failureMessage"))
    }

    /**
     * Determines the optimal audio codec and target container extension for frame-accurate re-encoding.
     */
    private fun getAudioCodecAndExtension(extension: String): Pair<String, String> {
        return when (extension.lowercase(Locale.US)) {
            "opus" -> Pair("-c:a opus -b:a 128k", "opus")
            "ogg" -> Pair("-c:a libvorbis -b:a 160k", "ogg")
            "mp3" -> Pair("-c:a libmp3lame -b:a 192k", "mp3")
            "wav" -> Pair("-c:a pcm_s16le", "wav")
            "flac" -> Pair("-c:a flac", "flac")
            "m4a", "aac" -> Pair("-c:a aac -b:a 192k", "m4a")
            else -> Pair("-c:a aac -b:a 192k", "m4a")
        }
    }

    private suspend fun executeSession(
        command: String,
        targetDurationMs: Long,
        onProgress: (TrimProgress) -> Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                lastSession = completedSession
                lastReturnCode = completedSession.returnCode
                activeSession = null
                if (ReturnCode.isSuccess(completedSession.returnCode)) {
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            },
            { log ->
                // Filter out verbose logs, print only important info
                if (log.message.contains("error", ignoreCase = true)) {
                    Log.e(TAG, "FFmpeg: ${log.message}")
                }
            },
            { statistics ->
                val timeProcessedMs = statistics.time.toLong()
                val percent = if (targetDurationMs > 0) {
                    ((timeProcessedMs.toFloat() / targetDurationMs.toFloat()) * 100f).coerceIn(0f, 100f)
                } else {
                    0f
                }
                val speed = String.format(Locale.US, "%.1fx", statistics.speed)
                val bitrate = String.format(Locale.US, "%.0f kb/s", statistics.bitrate)

                onProgress(
                    TrimProgress(
                        percent = percent,
                        timeProcessedMs = timeProcessedMs,
                        totalDurationMs = targetDurationMs,
                        speed = speed,
                        bitrate = bitrate
                    )
                )
            }
        )

        activeSession = session

        continuation.invokeOnCancellation {
            Log.d(TAG, "Cancelling active FFmpeg session: ${session.sessionId}")
            session.cancel()
        }
    }

    fun cancel() {
        activeSession?.let {
            Log.d(TAG, "Manually cancelling FFmpeg session: ${it.sessionId}")
            it.cancel()
            activeSession = null
        }
    }

    /** Reads the duration of [file] in milliseconds, or -1 if it can't be determined. */
    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe output duration: ${e.message}")
            -1L
        } finally {
            try { retriever.release() } catch (_: Exception) { /* ignore */ }
        }
    }

    companion object {
        private const val TAG = "FFmpegTrimmer"
    }
}
