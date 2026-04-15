package com.lonx.lyrico.utils

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlin.math.log10
import kotlin.math.pow
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ReplayGainAnalysis(
    val loudnessLufs: Double,
    val sampleCount: Long,
    val peak: Double
)
sealed interface ReplayGainCalculateState {
    data class Progress(val percent: Float) : ReplayGainCalculateState

    data class Success(
        val analysis: ReplayGainAnalysis,
        val mimeType: String
    ) : ReplayGainCalculateState

    data class UnsupportedCodec(
        val mimeType: String?
    ) : ReplayGainCalculateState

    data class Failed(
        val mimeType: String?,
        val message: String?
    ) : ReplayGainCalculateState
}

class ReplayGainScanner(
    private val context: Context
) {
    companion object {
        private const val TARGET_LOUDNESS_LUFS = -18.0
        // 进度更新阈值：每增加 1% (0.01) 才通知一次 UI，防止过度绘制卡顿
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
                emit(ReplayGainCalculateState.Failed(null, "No audio track found"))
                return@flow
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            // 获取音频总时长 (微秒)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime == null) {
                emit(ReplayGainCalculateState.Failed(null, "Unknown audio MIME type"))
                return@flow
            }
            mimeType = mime

            val decoderName = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format)
            if (decoderName == null) {
                emit(ReplayGainCalculateState.UnsupportedCodec(mime))
                return@flow
            }

            codec = MediaCodec.createByCodecName(decoderName).apply {
                configure(format, null, null, 0)
                start()
            }
            val codecRef = codec

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            var lastReportedProgress = 0.0

            // 刚开始解码，发射 0%
            emit(ReplayGainCalculateState.Progress(0f))

            while (!outputEnded) {
                // 1. 喂入压缩数据 (Input)
                if (!inputEnded) {
                    val inputIndex = codecRef.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codecRef.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codecRef.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            codecRef.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                // 取出解码后的 PCM 数据 (Output)
                when (val outputIndex = codecRef.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codecRef.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                        // 重置并初始化底层 C++ 库引擎
                        ebuR128?.close()
                        ebuR128 = LibEbuR128(channels, sampleRate)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            // 边缘保护：部分机型/系统版本可能不抛出 INFO_OUTPUT_FORMAT_CHANGED
                            if (ebuR128 == null) {
                                val outFmt = codecRef.outputFormat
                                val sr = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val ch = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                ebuR128 = LibEbuR128(ch, sr)
                            }

                            val outputBuffer = codecRef.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                // 限制 ByteBuffer 的可视区域为当前帧的有效数据
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                // 生成 DirectBuffer 切片，用于 JNI 层的 Zero-Copy（零拷贝）
                                val sliceBuffer = outputBuffer.slice()

                                val isFloat = (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT)
                                val bytesPerSample = if (isFloat) 4 else 2
                                val channels = ebuR128.channels
                                // 1 帧 (frame) = 所有声道在同一时刻的一个采样点组合
                                val frameCount = bufferInfo.size / (channels * bytesPerSample)

                                // 调用底层 C++ 进行极速计算
                                ebuR128.processDirect(sliceBuffer, isFloat, frameCount)

                                // 计算并发出进度 (节流机制)
                                if (durationUs > 0) {
                                    val currentUs = bufferInfo.presentationTimeUs
                                    val currentProgress = (currentUs.toDouble() / durationUs).coerceIn(0.0, 1.0)
                                    // 仅当进度增长大于阈值 (如1%) 时才发射，避免过度占用主线程
                                    if (currentProgress - lastReportedProgress >= PROGRESS_UPDATE_THRESHOLD) {
                                        emit(ReplayGainCalculateState.Progress(currentProgress.toFloat()))
                                        lastReportedProgress = currentProgress
                                    }
                                }
                            }

                            codecRef.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEnded = true
                            }
                        }
                    }
                }
            }

            // 发射 100%
            emit(ReplayGainCalculateState.Progress(1.0f))

            if (ebuR128 == null || ebuR128.sampleCount == 0L) {
                emit(ReplayGainCalculateState.Failed(mimeType, "Decoded sample count is zero"))
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
        } catch (e: IllegalStateException) {
            emit(ReplayGainCalculateState.Failed(mimeType, mapCodecStateError(mimeType, e.message)))
        } catch (e: Exception) {
            emit(ReplayGainCalculateState.Failed(mimeType, e.message))
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
     * 计算并格式化 Album Gain
     * @param trackLoudnesses 整张专辑所有歌曲的 track loudness (LUFS) 集合
     */
    fun formatAlbumGain(trackLoudnesses: List<Double>): String {
        if (trackLoudnesses.isEmpty()) return "0.00 dB"

        // EBU R128 标准专辑近似算法：将所有单曲的 LUFS 还原为能量均值，然后再转换为 LUFS
        val meanEnergy = trackLoudnesses.map { 10.0.pow((it + 0.691) / 10.0) }.average()
        val albumLoudness = -0.691 + 10 * log10(meanEnergy.coerceAtLeast(1e-10))
        val gainDb = TARGET_LOUDNESS_LUFS - albumLoudness

        return "%.2f dB".format(java.util.Locale.US, gainDb)
    }

    /**
     * 格式化 True Peak (真实峰值)
     */
    fun formatPeak(peak: Double): String {
        // True Peak 可能会因为插值超过 1.0 (0 dBFS 以上)，所以不需要上限截断
        return "%.6f".format(java.util.Locale.US, peak.coerceAtLeast(0.0))
    }

    fun buildFailureMessage(result: ReplayGainCalculateState): String {
        return when (result) {
            is ReplayGainCalculateState.Success -> "ReplayGain 标签已生成，可继续手动调整"
            is ReplayGainCalculateState.UnsupportedCodec -> {
                val formatName = describeMimeType(result.mimeType)
                "当前设备不支持解码 $formatName，暂时无法扫描 ReplayGain"
            }
            is ReplayGainCalculateState.Failed -> {
                val formatName = describeMimeType(result.mimeType)
                if (result.message.isNullOrBlank()) {
                    "扫描 ReplayGain 失败: $formatName"
                } else {
                    "扫描 ReplayGain 失败: $formatName，${result.message}"
                }
            }
            else -> ""
        }
    }

    private fun describeMimeType(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "audio/alac" -> "ALAC (m4a)"
            "audio/raw" -> "PCM/WAV"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm" -> "AAC/MP4"
            null -> "未知格式"
            else -> mimeType
        }
    }

    private fun mapCodecStateError(mimeType: String?, message: String?): String {
        if (message.isNullOrBlank()) {
            return "解码器进入异常状态"
        }

        return when {
            mimeType.equals("audio/alac", ignoreCase = true) &&
                    message.contains("Executing states", ignoreCase = true) -> {
                "当前设备上的 ALAC 解码器不可用或不稳定"
            }
            else -> message
        }
    }
}