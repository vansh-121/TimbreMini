package com.application.timbremini.domain

import android.content.Context
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
            Log.d(TAG, "Stream copy succeeded. Output size: ${outputFile.length()} bytes")
            return@withContext Result.success(outputFile)
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

        val reencodeCommand = if (isVideo) {
            "-y -ss $startSec -to $endSec -i \"${inputFile.absolutePath}\" -c:v libx264 -preset ultrafast -c:a aac \"${outputFile.absolutePath}\""
        } else {
            "-y -ss $startSec -to $endSec -i \"${inputFile.absolutePath}\" -c:a aac -b:a 192k \"${outputFile.absolutePath}\""
        }

        Log.d(TAG, "Executing FFmpeg Re-encode command: $reencodeCommand")
        val reencodeResult = executeSession(reencodeCommand, targetDurationMs, onProgress)

        if (reencodeResult && outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Re-encode succeeded. Output size: ${outputFile.length()} bytes")
            Result.success(outputFile)
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

    companion object {
        private const val TAG = "FFmpegTrimmer"
    }
}
