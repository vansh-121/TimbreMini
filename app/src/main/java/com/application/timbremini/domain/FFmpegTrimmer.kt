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
        val endSec = String.format(Locale.US, "%.3f", endMs / 1000.0)

        // Strategy 1: Fast Lossless Stream Copy (-c copy)
        // -avoid_negative_ts 1 ensures monotonic timestamps starting at zero
        val copyCommand = "-y -ss $startSec -to $endSec -i \"${inputFile.absolutePath}\" -c copy -avoid_negative_ts 1 \"${outputFile.absolutePath}\""
        Log.d(TAG, "Executing FFmpeg Stream Copy command: $copyCommand")

        val copyResult = executeSession(copyCommand, targetDurationMs, onProgress)

        if (copyResult && outputFile.exists() && outputFile.length() > 0) {
            // Stream copy can only cut on keyframes, so the start snaps to the nearest
            // preceding keyframe. With a large GOP this inflates the clip well beyond the
            // requested range (e.g. a 1s selection exporting as ~9s). Verify the real
            // output duration and only accept the copy when it matches the request; otherwise
            // fall through to a frame-accurate re-encode.
            val actualMs = probeDurationMs(outputFile)
            val tolerance = maxOf(300L, targetDurationMs / 20)
            if (actualMs > 0 && kotlin.math.abs(actualMs - targetDurationMs) <= tolerance) {
                Log.d(TAG, "Stream copy accepted. Target=${targetDurationMs}ms actual=${actualMs}ms size=${outputFile.length()}")
                return@withContext Result.success(outputFile)
            }
            // actualMs <= 0 means the container is unparseable (a broken copy that would save
            // as a phantom file that can't be opened), so we re-encode in that case too.
            Log.w(TAG, "Stream copy rejected (target=${targetDurationMs}ms actual=${actualMs}ms). Re-encoding for accuracy.")
        }

        // If stream copy was cancelled by the user, abort instead of re-encoding.
        if (ReturnCode.isCancel(lastReturnCode)) {
            return@withContext Result.failure(Exception("Trimming cancelled by user"))
        }

        // Strategy 2: Fallback to Precise Re-encoding if stream copy fails
        Log.w(TAG, "Stream copy failed or incomplete. Falling back to precise re-encoding...")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Re-encoded H.264/AAC must live in a compatible container. The source extension may be
        // .webm/.mkv (which can't hold H.264 reliably and which some devices' MediaStore rejects),
        // so video re-encodes always target .mp4. Audio keeps the working output path.
        val reencodeFile = if (isVideo) {
            File(context.cacheDir, "trimmed_temp_${System.currentTimeMillis()}.mp4")
        } else {
            outputFile
        }

        val reencodeCommand = if (isVideo) {
            // This ffmpeg-kit build ships without GPL codecs (no libx264/libx265), so we use
            // the bundled OpenH264 encoder. NOTE: -preset is an x264/x265 option and this build
            // rejects it ("Unrecognized option 'preset'"), so it must not be passed here.
            "-y -ss $startSec -to $endSec -i \"${inputFile.absolutePath}\" -c:v libopenh264 -b:v 6M -pix_fmt yuv420p -c:a aac -b:a 192k \"${reencodeFile.absolutePath}\""
        } else {
            "-y -ss $startSec -to $endSec -i \"${inputFile.absolutePath}\" -c:a aac -b:a 192k \"${reencodeFile.absolutePath}\""
        }

        Log.d(TAG, "Executing FFmpeg Re-encode command: $reencodeCommand")
        val reencodeResult = executeSession(reencodeCommand, targetDurationMs, onProgress)

        if (reencodeResult && reencodeFile.exists() && reencodeFile.length() > 0 && probeDurationMs(reencodeFile) > 0) {
            Log.d(TAG, "Re-encode succeeded. Output size: ${reencodeFile.length()} bytes")
            Result.success(reencodeFile)
        } else {
            val failureMessage = lastSession?.failStackTrace
                ?: lastSession?.allLogsAsString
                ?: "FFmpeg execution failed with return code: $lastReturnCode"
            Log.e(TAG, "FFmpeg trimming failed: $failureMessage")
            Result.failure(Exception("Trimming failed: $failureMessage"))
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
