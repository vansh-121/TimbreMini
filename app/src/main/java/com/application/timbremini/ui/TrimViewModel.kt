package com.application.timbremini.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.application.timbremini.R
import com.application.timbremini.data.MediaItemData
import com.application.timbremini.data.MediaStorageManager
import com.application.timbremini.data.TrimResult
import com.application.timbremini.data.TrimState
import com.application.timbremini.domain.FFmpegTrimmer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrimViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = MediaStorageManager(application)
    private val ffmpegTrimmer = FFmpegTrimmer(application)

    private val _selectedMedia = MutableStateFlow<MediaItemData?>(null)
    val selectedMedia: StateFlow<MediaItemData?> = _selectedMedia.asStateFlow()

    private val _startMs = MutableStateFlow(0L)
    val startMs: StateFlow<Long> = _startMs.asStateFlow()

    private val _endMs = MutableStateFlow(0L)
    val endMs: StateFlow<Long> = _endMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoopTrimActive = MutableStateFlow(true)
    val isLoopTrimActive: StateFlow<Boolean> = _isLoopTrimActive.asStateFlow()

    private val _trimState = MutableStateFlow<TrimState>(TrimState.Idle)
    val trimState: StateFlow<TrimState> = _trimState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var positionTrackerJob: Job? = null
    private var trimmingJob: Job? = null

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(getApplication()).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            // Reached the natural end of the media; loop back only if enabled.
                            if (_isLoopTrimActive.value) {
                                seekTo(_startMs.value)
                                play()
                            } else {
                                pause()
                                seekTo(_endMs.value)
                            }
                        }
                    }
                })
            }
            startPositionTracker()
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    val pos = p.currentPosition
                    _currentPositionMs.value = pos

                    // Playback is always constrained to the trim selection. When the
                    // playhead passes the out-point, loop back if enabled, otherwise stop.
                    if (p.isPlaying) {
                        val end = _endMs.value
                        val start = _startMs.value
                        if (end > start && pos >= end) {
                            if (_isLoopTrimActive.value) {
                                p.seekTo(start)
                            } else {
                                p.pause()
                                p.seekTo(end)
                                _currentPositionMs.value = end
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    fun loadMedia(uri: Uri) {
        viewModelScope.launch {
            _trimState.value = TrimState.Preparing(getString(R.string.analyzing))
            _errorMessage.value = null

            // Discard any previously cached source/temp files before importing the new one.
            storageManager.cleanCache()

            val result = storageManager.prepareMediaItem(uri)
            result.onSuccess { item ->
                _selectedMedia.value = item
                _startMs.value = 0L
                _endMs.value = item.durationMs.coerceAtLeast(1000L)
                _trimState.value = TrimState.Idle

                // Prepare ExoPlayer with cached local file
                player?.let { p ->
                    p.stop()
                    p.clearMediaItems()
                    p.setMediaItem(MediaItem.fromUri(Uri.fromFile(item.cachedFile)))
                    p.prepare()
                    p.seekTo(0)
                }
            }.onFailure { error ->
                _trimState.value = TrimState.Idle
                _errorMessage.value = error.message ?: getString(R.string.err_load)
            }
        }
    }

    fun updateTrimRange(start: Long, end: Long) {
        val media = _selectedMedia.value ?: return
        val total = media.durationMs
        val validatedStart = start.coerceIn(0L, total)
        val validatedEnd = end.coerceIn(validatedStart + 500L, total)

        _startMs.value = validatedStart
        _endMs.value = validatedEnd

        // If player head is outside new range, snap to start
        val current = _currentPositionMs.value
        if (current < validatedStart || current > validatedEnd) {
            seekTo(validatedStart)
        }
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                // Always play the selection: if the head is outside the range, restart from the in-point.
                val current = p.currentPosition
                if (current < _startMs.value || current >= _endMs.value) {
                    p.seekTo(_startMs.value)
                }
                p.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun seekToStart() {
        seekTo(_startMs.value)
    }

    fun seekToEnd() {
        seekTo(_endMs.value)
    }

    fun toggleLoopTrim() {
        _isLoopTrimActive.value = !_isLoopTrimActive.value
    }

    fun startTrim() {
        val media = _selectedMedia.value ?: return
        val start = _startMs.value
        val end = _endMs.value

        if (end - start < 500L) {
            _errorMessage.value = getString(R.string.err_min_duration)
            return
        }

        // Pause playback before trimming
        player?.pause()

        trimmingJob?.cancel()
        trimmingJob = viewModelScope.launch {
            _trimState.value = TrimState.Preparing(getString(R.string.preparing))

            val trimDuration = end - start
            val trimResult = ffmpegTrimmer.trim(
                inputFile = media.cachedFile,
                startMs = start,
                endMs = end,
                isVideo = media.isVideo,
                onProgress = { progress ->
                    _trimState.value = TrimState.Processing(progress)
                }
            )

            trimResult.onSuccess { trimmedFile ->
                _trimState.value = TrimState.Saving(getString(R.string.saving))

                val saveResult = storageManager.saveTrimmedFileToDeviceStorage(
                    trimmedFile = trimmedFile,
                    originalName = media.name,
                    isVideo = media.isVideo,
                    durationMs = trimDuration
                )

                saveResult.onSuccess { (outputUri, displayPath) ->
                    _trimState.value = TrimState.Success(
                        TrimResult(
                            outputUri = outputUri,
                            outputPath = displayPath,
                            fileSize = trimmedFile.length(),
                            durationMs = trimDuration,
                            isVideo = media.isVideo,
                            localFile = trimmedFile
                        )
                    )
                }.onFailure { error ->
                    _trimState.value = TrimState.Error("Failed to save to storage: ${error.message}")
                }
            }.onFailure { error ->
                _trimState.value = TrimState.Error(error.message ?: "Trimming failed")
            }
        }
    }

    fun cancelTrim() {
        ffmpegTrimmer.cancel()
        trimmingJob?.cancel()
        _trimState.value = TrimState.Idle
    }

    fun dismissResult() {
        _trimState.value = TrimState.Idle
    }

    fun dismissError() {
        _errorMessage.value = null
        if (_trimState.value is TrimState.Error) {
            _trimState.value = TrimState.Idle
        }
    }

    /**
     * Clears the current selection and returns to the home/empty screen. Stops playback,
     * resets the trim range and state, and frees the cached source file.
     */
    fun clearSelection() {
        trimmingJob?.cancel()
        ffmpegTrimmer.cancel()
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        _selectedMedia.value = null
        _startMs.value = 0L
        _endMs.value = 0L
        _currentPositionMs.value = 0L
        _isPlaying.value = false
        _trimState.value = TrimState.Idle
        _errorMessage.value = null
        storageManager.cleanCache()
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    override fun onCleared() {
        super.onCleared()
        positionTrackerJob?.cancel()
        trimmingJob?.cancel()
        player?.release()
        player = null
        storageManager.cleanCache()
    }
}
