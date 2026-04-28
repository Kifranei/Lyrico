package com.lonx.lyrico.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.ReplayGainAnalysis
import com.lonx.lyrico.utils.ReplayGainCalculateState
import com.lonx.lyrico.utils.ReplayGainScanner
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class BatchReplayGainUiState(
    val isRunning: Boolean = false,
    val concurrency: Int = 3,
    val progress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeMillis: Long = 0,
    val showConfigDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val isSuccess: Boolean = false,
    val resultMessage: UiMessage? = null,
    val startTimeMillis: Long = 0L,  // 记录开始时间
    val finalTimeMillis: Long = 0L, // 记录结束时的最终总时间
    val fileProgressMap: Map<String, Float> = emptyMap(),
)

class BatchReplayGainViewModel(
    private val songRepository: SongRepository,
    private val replayGainScanner: ReplayGainScanner
) : ViewModel() {

    private val TAG = "BatchReplayGainVM"

    private var batchScanJob: Job? = null

    private val _uiState = MutableStateFlow(BatchReplayGainUiState())
    val uiState: StateFlow<BatchReplayGainUiState> = _uiState.asStateFlow()

    private var selectedUris: List<String> = emptyList()

    fun setSelectionUris(uris: List<String>) {
        selectedUris = uris
    }

    fun openReplayGainConfig() {
        _uiState.update { it.copy(showConfigDialog = true) }
    }

    fun closeReplayGainConfig() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun setConcurrency(count: Int) {
        _uiState.update { it.copy(concurrency = count.coerceIn(1, 5)) }
    }

    private suspend fun isReplayGainExisting(uri: String): Boolean {
        return try {
            val song = songRepository.getSongByUri(uri)
            val rawProps = song?.rawProperties ?: return false
            rawProps.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true) ||
            rawProps.contains("\"replayGainTrackGain\"", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    fun startBatchScan() {
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        val concurrency = _uiState.value.concurrency
        val startTime = SystemClock.elapsedRealtime()
        batchScanJob = viewModelScope.launch {
            val (songsToScan, songsToSkip) = uris.partition { isReplayGainExisting(it).not() }
            val total = songsToScan.size
            val skipCount = songsToSkip.size

            if (total == 0) {
                _uiState.update {
                    it.copy(
                        showConfigDialog = false,
                        showProgressDialog = true,
                        isRunning = false,
                        progress = 0 to 0,
                        successCount = 0,
                        failureCount = 0,
                        skippedCount = skipCount,
                        startTimeMillis = startTime,
                        finalTimeMillis = 0L
                    )
                }
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val processedCount = AtomicInteger(0)
            val successCounter = AtomicInteger(0)
            val failureCounter = AtomicInteger(0)

            _uiState.update {
                it.copy(
                    showConfigDialog = false,
                    showProgressDialog = true,
                    isRunning = true,
                    progress = 0 to total,
                    successCount = 0,
                    failureCount = 0,
                    skippedCount = skipCount,
                    fileProgressMap = emptyMap(),
                    totalTimeMillis = 0
                )
            }

            val analyzeSemaphore = Semaphore(concurrency)

            try {
                val allJobs = songsToScan.map { uri ->
                    launch(Dispatchers.IO) {
                        analyzeSemaphore.withPermit {
                            val song = try {
                                songRepository.getSongByUri(uri)
                            } catch (e: Exception) {
                                null
                            }
                            val fileName = song?.fileName ?: uri.substringAfterLast("/")

                            var analysisSuccess = false
                            var analysisResult: ReplayGainAnalysis? = null

                            try {
                                replayGainScanner.analyze(uri).collect { state ->
                                    when (state) {
                                        is ReplayGainCalculateState.Progress -> {
                                            _uiState.update { 
                                                it.copy(fileProgressMap = it.fileProgressMap + (fileName to state.percent)) 
                                            }
                                        }
                                        is ReplayGainCalculateState.Success -> {
                                            analysisResult = state.analysis
                                            analysisSuccess = true
                                        }
                                        is ReplayGainCalculateState.Cancelled,
                                        is ReplayGainCalculateState.Failed -> {
                                            analysisSuccess = false
                                        }
                                    }
                                }

                                if (analysisSuccess && analysisResult != null) {
                                    _uiState.update { 
                                        it.copy(
                                            fileProgressMap = it.fileProgressMap - fileName
                                        ) 
                                    }
                                    val tagData = AudioTagData(
                                        replayGainTrackGain = replayGainScanner.formatGain(analysisResult),
                                        replayGainTrackPeak = replayGainScanner.formatPeak(analysisResult.peak),
                                        replayGainReferenceLoudness = "-18 LUFS"
                                    )

                                    val writeSuccess = songRepository.patchAudioTags(uri, tagData)
                                    if (writeSuccess) {
                                        successCounter.incrementAndGet()
                                    } else {
                                        failureCounter.incrementAndGet()
                                    }
                                    Log.d(TAG, "Song $fileName processed ${if (writeSuccess) "successfully" else "failed"}")
                                } else {
                                    failureCounter.incrementAndGet()
                                    Log.d(TAG, "Song $fileName analysis failed")
                                }
                            } catch (e: CancellationException) {
                                Log.d(TAG, "Analysis cancelled: $fileName")
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Processing failed: $uri", e)
                                failureCounter.incrementAndGet()
                            } finally {
                                val current = processedCount.incrementAndGet()
                                val successCount = successCounter.get()
                                val failureCount = failureCounter.get()
                                _uiState.update {
                                    it.copy(
                                        progress = current to total,
                                        successCount = successCount,
                                        failureCount = failureCount,
                                        fileProgressMap = it.fileProgressMap - fileName
                                    )
                                }
                            }
                        }
                    }
                }
                allJobs.joinAll()
            } finally {
                withContext(NonCancellable) {
                    val totalTime = System.currentTimeMillis() - startTime
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            totalTimeMillis = totalTime,
                            successCount = successCounter.get(),
                            failureCount = failureCounter.get(),
                            isSuccess = failureCounter.get() == 0
                        )
                    }
                }
            }
        }
    }

    fun abortBatchScan() {
        batchScanJob?.cancel()
        batchScanJob = null
    }

    fun closeProgressDialog() {
        _uiState.update {
            it.copy(
                showProgressDialog = false,
                progress = null,
                isRunning = false,
                finalTimeMillis = 0
            )
        }
    }

    override fun onCleared() {
        batchScanJob?.cancel()
        super.onCleared()
    }
}