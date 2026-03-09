package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import kotlin.math.abs

object LyricsUtils {
    @SuppressLint("DefaultLocale")
    private fun formatTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }

    /**
     * 计算应用偏移量，保证结果大于等于 0
     */
    private fun applyOffset(time: Long, offset: Long): Long {
        return (time + offset).coerceAtLeast(0L)
    }

    private fun isBlankOrPlaceholder(line: LyricsLine): Boolean {
        val text = line.words.joinToString("") { it.text }.trim()
        return text.isEmpty() || text.matches(Regex("^[\\s/]*$"))
    }

    fun formatLrcResult(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long = 0L,
    ): String {
        val builder = StringBuilder()

        val romanMap = if (config.showRomanization) {
            result.romanization?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        val translatedMap = if (config.showTranslation) {
            result.translated?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        result.original.forEach { line ->
            if (config.removeEmptyLines && isBlankOrPlaceholder(line)) {
                return@forEach
            }

            val matchedTranslation = if (config.showTranslation) {
                val match = matchingSubLine(line, translatedMap)
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null

            val matchedRoman = if (config.showRomanization) {
                val match = matchingSubLine(line, romanMap)
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null

            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null

            if (!skipOriginal) {
                when (config.format) {
                    LyricFormat.ENHANCED_LRC -> appendEnhancedLine(builder, line, offset)
                    LyricFormat.PLAIN_LRC -> appendLineByLine(builder, line, offset)
                    LyricFormat.VERBATIM_LRC -> appendWordByWord(builder, line, offset)
                }
                builder.append('\n')
            }

            if (matchedRoman != null && !skipOriginal) {
                builder.append(formatPlainLine(matchedRoman, offset)).append('\n')
            }

            if (matchedTranslation != null) {
                builder.append(formatPlainLine(matchedTranslation, offset)).append('\n')
            }
        }
        return builder.toString().trim()
    }

    private fun appendEnhancedLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        if (line.words.isEmpty()) return

        // 行开始时间应用 offset
        builder.append("[${formatTimestamp(applyOffset(line.start, offset))}] ")

        line.words.forEach { word ->
            // 每个词的开始时间应用 offset
            builder.append("<${formatTimestamp(applyOffset(word.start, offset))}>")
            builder.append(word.text)
        }

        // 行结束时间应用 offset
        val lastEnd = line.words.last().end
        builder.append(" <${formatTimestamp(applyOffset(lastEnd, offset))}>")
    }

    private fun appendLineByLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        val lineText = line.words.joinToString("") { it.text }
        val endTime = line.words.lastOrNull()?.end

        // 应用 offset
        val startTimeFormatted = formatTimestamp(applyOffset(line.start, offset))

        if (endTime != null) {
            val endTimeFormatted = formatTimestamp(applyOffset(endTime, offset))
            builder.append("[$startTimeFormatted]$lineText[$endTimeFormatted]")
        } else {
            builder.append("[$startTimeFormatted]$lineText")
        }
    }

    private fun appendWordByWord(builder: StringBuilder, line: LyricsLine, offset: Long) {
        line.words.forEachIndexed { index, word ->
            val startFormatted = formatTimestamp(applyOffset(word.start, offset))
            if (index == line.words.lastIndex) {
                val endFormatted = formatTimestamp(applyOffset(word.end, offset))
                builder.append("[$startFormatted]${word.text}[$endFormatted]")
            } else {
                builder.append("[$startFormatted]${word.text}")
            }
        }
    }

    private fun formatPlainLine(line: LyricsLine, offset: Long): String {
        val startFormatted = formatTimestamp(applyOffset(line.start, offset))
        return "[$startFormatted]" + line.words.joinToString(" ") { it.text }
    }

    private fun matchingSubLine(
        originalLine: LyricsLine,
        subLineMap: Map<Long, LyricsLine>
    ): LyricsLine? {
        val matched = subLineMap[originalLine.start]
        if (matched != null) return matched
        return subLineMap.entries.find { abs(it.key - originalLine.start) < 300 }?.value
    }
}