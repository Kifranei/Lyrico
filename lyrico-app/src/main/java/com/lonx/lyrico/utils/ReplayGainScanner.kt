package com.lonx.lyrico.utils

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

data class ReplayGainAnalysis(
    val loudnessLufs: Double,
    val sampleCount: Long,
    val peak: Double
)

// 定义具体的错误原因，方便调用方根据枚举或类名进行多语言/自定义处理
sealed interface ReplayGainError {
    data object NoAudioTrack : ReplayGainError
    data object UnknownMimeType : ReplayGainError
    data object ZeroSampleCount : ReplayGainError
    data class UnsupportedCodec(val mimeType: String?) : ReplayGainError
    data class CodecException(val message: String?, val isAlacIssue: Boolean = false) : ReplayGainError
    data class GeneralException(val message: String?) : ReplayGainError
}

sealed interface ReplayGainCalculateState {
    data class Progress(val percent: Float) : ReplayGainCalculateState
    data object Cancelled : ReplayGainCalculateState
    data class Success(
        val analysis: ReplayGainAnalysis,
        val mimeType: String
    ) : ReplayGainCalculateState

    // 将失败统一为一个状态，携带错误原因对象
    data class Failed(
        val mimeType: String?,
        val error: ReplayGainError
    ) : ReplayGainCalculateState
}

class ReplayGainScanner(private val context: Context) {
    companion object {
        private const val TARGET_LOUDNESS_LUFS = -18.0
        private const val PROGRESS_UPDATE_THRESHOLD = 0.01
    }

    fun analyze(uriString: String): Flow<ReplayGainCalculateState> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var mimeType: String? = null
        var ebuR128: LibEbuR128? = null

        try {
            extractor.setDataSource(context, uriString.toUri(), null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }

            if (trackIndex == null) {
                emit(ReplayGainCalculateState.Failed(null, ReplayGainError.NoAudioTrack))
                return@flow
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            mimeType = format.getString(MediaFormat.KEY_MIME)
            if (mimeType == null) {
                emit(ReplayGainCalculateState.Failed(null, ReplayGainError.UnknownMimeType))
                return@flow
            }

            val decoderName = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format)
            if (decoderName == null) {
                emit(ReplayGainCalculateState.Failed(mimeType, ReplayGainError.UnsupportedCodec(mimeType)))
                return@flow
            }

            codec = MediaCodec.createByCodecName(decoderName).apply {
                configure(format, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var lastReportedProgress = 0.0

            emit(ReplayGainCalculateState.Progress(0f))

            while (!outputEnded) {
                // 检查协程是否被取消，如果取消会抛出 CancellationException
                currentCoroutineContext().ensureActive()

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        pcmEncoding = if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        val sr = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val ch = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        ebuR128?.close()
                        ebuR128 = LibEbuR128(ch, sr)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (ebuR128 == null) {
                            val outFmt = codec.outputFormat
                            ebuR128 = LibEbuR128(outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT), outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                        }

                        codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                            if (bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val isFloat = (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT)
                                val frameCount = bufferInfo.size / (ebuR128.channels * (if (isFloat) 4 else 2))
                                ebuR128.processDirect(outputBuffer.slice(), isFloat, frameCount)

                                if (durationUs > 0) {
                                    val currentProgress = (bufferInfo.presentationTimeUs.toDouble() / durationUs).coerceIn(0.0, 1.0)
                                    if (currentProgress - lastReportedProgress >= PROGRESS_UPDATE_THRESHOLD) {
                                        emit(ReplayGainCalculateState.Progress(currentProgress.toFloat()))
                                        lastReportedProgress = currentProgress
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    }
                }
            }

            emit(ReplayGainCalculateState.Progress(1.0f))

            if (ebuR128 == null || ebuR128.sampleCount == 0L) {
                emit(ReplayGainCalculateState.Failed(mimeType, ReplayGainError.ZeroSampleCount))
            } else {
                emit(
                    ReplayGainCalculateState.Success(
                        analysis = ReplayGainAnalysis(
                            loudnessLufs = ebuR128.loudness,
                            sampleCount = ebuR128.sampleCount,
                            peak = ebuR128.truePeak
                        ),
                        mimeType = mimeType
                    )
                )
            }

        } catch (e: CancellationException) {
            // 捕获取消异常，发射 Cancelled 状态并重新抛出
            emit(ReplayGainCalculateState.Cancelled)
            throw e
        } catch (e: IllegalStateException) {
            val isAlacIssue = mimeType.equals("audio/alac", true) && e.message?.contains("Executing states", true) == true
            emit(ReplayGainCalculateState.Failed(mimeType, ReplayGainError.CodecException(e.message, isAlacIssue)))
        } catch (e: Exception) {
            emit(ReplayGainCalculateState.Failed(mimeType, ReplayGainError.GeneralException(e.message)))
        } finally {
            runCatching { ebuR128?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * 将解析出的 LUFS 响度格式化为 Track Gain
     */
    fun formatGain(analysis: ReplayGainAnalysis): String {
        // Gain = 目标参考响度 - 实际测量响度
        val gainDb = TARGET_LOUDNESS_LUFS - analysis.loudnessLufs
        return "%.2f dB".format(java.util.Locale.US, gainDb)
    }

    /**
     * 格式化 True Peak (真实峰值)
     */
    fun formatPeak(peak: Double): String {
        // True Peak 可能会因为插值超过 1.0 (0 dBFS 以上)，所以不需要上限截断
        return "%.6f".format(java.util.Locale.US, peak.coerceAtLeast(0.0))
    }

}
