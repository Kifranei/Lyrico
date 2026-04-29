package com.lonx.lyrico.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrics.model.isWordByWord
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

data class BatchLyricsFormatUiState(
    val isRunning: Boolean = false,
    val concurrency: Int = 3,
    val targetFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
    val progress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeMillis: Long = 0,
    val showConfigDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val isSuccess: Boolean = false,
    val activeFiles: List<String> = emptyList(),
)

class BatchLyricsFormatViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    private val tag = "BatchLyricsFormatVM"
    private var batchConvertJob: Job? = null

    private val _uiState = MutableStateFlow(BatchLyricsFormatUiState())
    val uiState: StateFlow<BatchLyricsFormatUiState> = _uiState.asStateFlow()

    private var selectedUris: List<String> = emptyList()

    fun setSelectionUris(uris: List<String>) {
        selectedUris = uris
    }

    fun openConfig(initialConcurrency: Int) {
        _uiState.update {
            it.copy(
                concurrency = initialConcurrency.coerceIn(1, 5),
                showConfigDialog = true
            )
        }
    }

    fun closeConfig() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun setConcurrency(count: Int) {
        _uiState.update { it.copy(concurrency = count.coerceIn(1, 5)) }
    }

    fun setTargetFormat(targetFormat: LyricFormat) {
        _uiState.update { it.copy(targetFormat = targetFormat) }
    }

    fun startBatchConvert() {
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        val concurrency = _uiState.value.concurrency
        val targetFormat = _uiState.value.targetFormat

        batchConvertJob = viewModelScope.launch {
            val total = uris.size
            val processedCount = AtomicInteger(0)
            val successCounter = AtomicInteger(0)
            val failureCounter = AtomicInteger(0)
            val skippedCounter = AtomicInteger(0)
            val startTime = SystemClock.elapsedRealtime()
            val semaphore = Semaphore(concurrency)

            _uiState.update {
                it.copy(
                    showConfigDialog = false,
                    showProgressDialog = true,
                    isRunning = true,
                    progress = 0 to total,
                    successCount = 0,
                    failureCount = 0,
                    skippedCount = 0,
                    totalTimeMillis = 0L,
                    isSuccess = false,
                    activeFiles = emptyList()
                )
            }

            try {
                val jobs = uris.map { uri ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            val fileName = songRepository.getDisplayName(uri)

                            _uiState.update {
                                it.copy(activeFiles = (it.activeFiles + fileName).distinct())
                            }

                            try {
                                val song = songRepository.getSongByUri(uri)
                                val lyrics = song?.lyrics
                                val currentFormat = lyrics?.let(LyricDecoder::detectFormat)

                                val result = when {
                                    lyrics.isNullOrBlank() -> ProcessResult.Skipped
                                    currentFormat == targetFormat -> ProcessResult.Skipped
                                    else -> {
                                        val convertedLyrics =
                                            convertLyricsFormat(lyrics, targetFormat)
                                        if (convertedLyrics == null || convertedLyrics == lyrics) {
                                            ProcessResult.Failed
                                        } else {
                                            val success = songRepository.patchAudioTags(
                                                uri,
                                                AudioTagData(lyrics = convertedLyrics)
                                            )
                                            if (success) {
                                                ProcessResult.Success
                                            } else {
                                                ProcessResult.Failed
                                            }
                                        }
                                    }
                                }

                                when (result) {
                                    ProcessResult.Success -> successCounter.incrementAndGet()
                                    ProcessResult.Failed -> failureCounter.incrementAndGet()
                                    ProcessResult.Skipped -> skippedCounter.incrementAndGet()
                                }
                            } catch (e: CancellationException) {
                                Log.d(tag, "Lyrics format conversion cancelled: $fileName")
                                throw e
                            } catch (e: Exception) {
                                Log.e(tag, "Lyrics format conversion failed: $uri", e)
                                failureCounter.incrementAndGet()
                            } finally {
                                val current = processedCount.incrementAndGet()
                                _uiState.update {
                                    it.copy(
                                        progress = current to total,
                                        successCount = successCounter.get(),
                                        failureCount = failureCounter.get(),
                                        skippedCount = skippedCounter.get(),
                                        activeFiles = it.activeFiles - fileName
                                    )
                                }
                            }
                        }
                    }
                }
                jobs.joinAll()
            } finally {
                withContext(NonCancellable) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            totalTimeMillis = SystemClock.elapsedRealtime() - startTime,
                            successCount = successCounter.get(),
                            failureCount = failureCounter.get(),
                            skippedCount = skippedCounter.get(),
                            isSuccess = failureCounter.get() == 0,
                            activeFiles = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun abortBatchConvert() {
        batchConvertJob?.cancel()
        batchConvertJob = null
    }

    fun closeProgressDialog() {
        _uiState.update {
            it.copy(
                showProgressDialog = false,
                progress = null,
                isRunning = false,
                activeFiles = emptyList()
            )
        }
    }

    override fun onCleared() {
        batchConvertJob?.cancel()
        super.onCleared()
    }

    private fun convertLyricsFormat(lyrics: String, targetFormat: LyricFormat): String? {
        return try {
            val lines = LyricDecoder.decode(lyrics)
            if (lines.isEmpty()) return null

            val lyricsResult = com.lonx.lyrics.model.LyricsResult(
                tags = emptyMap(),
                original = lines,
                translated = null,
                romanization = null,
                isWordByWord = lines.isWordByWord()
            )
            val config = LyricRenderConfig(
                format = targetFormat,
                conversionMode = ConversionMode.NONE,
                showTranslation = false,
                showRomanization = false,
                removeEmptyLines = false,
                onlyTranslationIfAvailable = false
            )
            LyricEncoder.encode(lyricsResult, config)
        } catch (e: Exception) {
            Log.e(tag, "Convert lyrics format failed", e)
            null
        }
    }

    private enum class ProcessResult {
        Success,
        Failed,
        Skipped
    }
}
