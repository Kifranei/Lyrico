package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsWord

object LyricDecoder {
    private val LRC_TIME_PATTERN = Regex("([<\\[])(\\d{2,}):(\\d{2})\\.(\\d{2,3})([>\\]])")
    private val TTML_P_PATTERN = Regex("""<p\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_SPAN_PATTERN = Regex("""<span\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

    // ==========================================
    // 1. 格式识别
    // ==========================================

    /**
     * 识别歌词文本的格式
     */
    fun detectFormat(lyricsText: String): LyricFormat? {
        if (lyricsText.isBlank()) return null

        val sampleLines = lyricsText.lines().filter { it.isNotBlank() }

        // 检查 TTML
        if (sampleLines.any { it.contains("<tt ") || it.contains("xmlns=\"http://www.w3.org/ns/ttml\"") }) {
            return LyricFormat.TTML
        }

        var hasEnhanced = false
        var hasVerbatim = false
        var hasPlain = false

        for (line in sampleLines) {
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")) continue

            val matches = LRC_TIME_PATTERN.findAll(line).toList()
            if (matches.isEmpty()) continue

            val hasBracket = matches.any { it.value.startsWith("[") }
            val hasAngle = matches.any { it.value.startsWith("<") }

            if (hasBracket && hasAngle) {
                hasEnhanced = true
                break
            } else if (hasBracket && matches.size > 1) {
                hasVerbatim = true
                break
            } else if (hasBracket && matches.size == 1) {
                hasPlain = true
            }
        }

        return when {
            hasEnhanced -> LyricFormat.ENHANCED_LRC
            hasVerbatim -> LyricFormat.VERBATIM_LRC
            hasPlain -> LyricFormat.PLAIN_LRC
            else -> null
        }
    }


    /**
     * 解析任意格式的歌词字符串为标准模型
     * 自动检测格式并选择对应的解析方法
     */
    fun decode(lyricsText: String): List<LyricsLine> {
        val format = detectFormat(lyricsText) ?: return emptyList()
        
        return when (format) {
            LyricFormat.TTML -> parseTtmlToModels(lyricsText)
            LyricFormat.PLAIN_LRC,
            LyricFormat.VERBATIM_LRC,
            LyricFormat.ENHANCED_LRC -> parseLrcToModels(lyricsText)
        }
    }


    /**
     * 将模型转换为指定格式的歌词字符串
     */
    fun generate(lines: List<LyricsLine>, format: LyricFormat): String {
        return when (format) {
            LyricFormat.TTML -> generateTtml(lines)
            LyricFormat.PLAIN_LRC,
            LyricFormat.VERBATIM_LRC,
            LyricFormat.ENHANCED_LRC -> generateLrc(lines, format)
        }
    }

    // ==========================================
    // 4. 便捷转换方法（String → String）
    // ==========================================

    /**
     * 逐字 (Verbatim) 转 增强逐字 (Enhanced)
     */
    fun verbatimToEnhanced(lyricsText: String): String {
        return lyricsText.lines().joinToString("\n") { line ->
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")) return@joinToString line

            val matches = LRC_TIME_PATTERN.findAll(line).toList()
            if (matches.size > 1 && matches.all { it.value.startsWith("[") }) {
                val firstTime = matches.first().value
                val convertedLine = line.replace("[", "<").replace("]", ">")
                "$firstTime $convertedLine"
            } else {
                line
            }
        }
    }

    /**
     * 增强逐字 (Enhanced) 转 逐字 (Verbatim)
     */
    fun enhancedToVerbatim(lyricsText: String): String {
        val enhancedPrefixPattern = Regex("""^\[\d{2,}:\d{2}\.\d{2,3}]\s*(?=<)""")
        return lyricsText.lines().joinToString("\n") { line ->
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")) return@joinToString line

            if (line.contains("<") && line.contains(">")) {
                val withoutPrefix = line.replaceFirst(enhancedPrefixPattern, "")
                withoutPrefix.replace("<", "[").replace(">", "]")
            } else {
                line
            }
        }
    }


    /**
     * 逐字 / 增强逐字 转换为 TTML
     */
    fun lrcToTtml(lyricsText: String): String {
        val lines = parseLrcToModels(lyricsText)
        return generateTtml(lines)
    }

    /**
     * TTML 转换为 LRC 格式（支持所有 LRC 类型）
     */
    fun ttmlToLrc(ttmlText: String, targetFormat: LyricFormat): String {
        require(targetFormat == LyricFormat.VERBATIM_LRC || 
                targetFormat == LyricFormat.ENHANCED_LRC || 
                targetFormat == LyricFormat.PLAIN_LRC) {
            "Target format must be VERBATIM_LRC, ENHANCED_LRC, or PLAIN_LRC"
        }
        val lines = parseTtmlToModels(ttmlText)
        return generateLrc(lines, targetFormat)
    }


    /**
     * 解析 LRC 文本到你自己的 LyricsLine 模型
     */
    fun parseLrcToModels(lyricsText: String): List<LyricsLine> {
        val parsedLines = mutableListOf<LyricsLine>()

        lyricsText.lines().forEach { lineStr ->
            if (lineStr.isBlank() || lineStr.startsWith("[ti:") || lineStr.startsWith("[ar:")) return@forEach

            val matches = LRC_TIME_PATTERN.findAll(lineStr).toList()
            if (matches.isEmpty()) return@forEach

            val words = mutableListOf<LyricsWord>()
            val lineStart = parseLrcTimeMs(matches.first())

            var isFirstEnhancedTag = true

            for (i in matches.indices) {
                val match = matches[i]
                val time = parseLrcTimeMs(match)
                val nextMatchIdx = if (i + 1 < matches.size) matches[i + 1].range.first else lineStr.length

                var text = lineStr.substring(match.range.last + 1, nextMatchIdx)

                if (isFirstEnhancedTag && text.trim().isEmpty() && match.value.startsWith("[")) {
                    isFirstEnhancedTag = false
                    continue
                }

                // 不移除空格，保留原始文本（包括单词间的空格）
                // 只有当文本全是空白时才跳过

                if (text.isNotEmpty() || (i == matches.size - 2 && text.isEmpty())) {
                    val nextTime = if (i + 1 < matches.size) parseLrcTimeMs(matches[i + 1]) else time + 500
                    if (text.isNotEmpty()) {
                        words.add(LyricsWord(start = time, end = nextTime, text = text))
                    }
                }
            }

            if (words.isNotEmpty()) {
                val lineEnd = words.last().end
                parsedLines.add(LyricsLine(start = lineStart, end = lineEnd, words = words))
            }
        }
        return parsedLines
    }

    /**
     * 解析 TTML 文本到你自己的 LyricsLine 模型
     */
    fun parseTtmlToModels(ttmlText: String): List<LyricsLine> {
        val parsedLines = mutableListOf<LyricsLine>()

        TTML_P_PATTERN.findAll(ttmlText).forEach { pMatch ->
            val pBegin = parseTtmlTimeMs(pMatch.groupValues[1])
            val pEnd = parseTtmlTimeMs(pMatch.groupValues[2])
            val innerHtml = pMatch.groupValues[3]

            val words = mutableListOf<LyricsWord>()
            TTML_SPAN_PATTERN.findAll(innerHtml).forEach { spanMatch ->
                val sBegin = parseTtmlTimeMs(spanMatch.groupValues[1])
                val sEnd = parseTtmlTimeMs(spanMatch.groupValues[2])
                val text = unescapeXml(spanMatch.groupValues[3])
                words.add(LyricsWord(start = sBegin, end = sEnd, text = text))
            }

            if (words.isNotEmpty()) {
                parsedLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
            } else {
                val text = unescapeXml(innerHtml.replace(Regex("<.*?>"), ""))
                if (text.isNotBlank()) {
                    words.add(LyricsWord(start = pBegin, end = pEnd, text = text))
                    parsedLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
                }
            }
        }
        return parsedLines
    }

    // ==========================================
    // 5. List<LyricsLine> 生成目标格式字符串
    // ==========================================

    /**
     * 从模型生成 TTML
     */
    fun generateTtml(lines: List<LyricsLine>): String {
        val builder = StringBuilder()

        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<tt xmlns=\"http://www.w3.org/ns/ttml\">\n")
        builder.append("  <body>\n    <div>\n")

        lines.forEach { line ->
            val beginStr = formatTtmlTimestamp(line.start)
            val endStr = formatTtmlTimestamp(line.end)
            builder.append("      <p begin=\"$beginStr\" end=\"$endStr\">")

            line.words.forEach { word ->
                val wBegin = formatTtmlTimestamp(word.start)
                val wEnd = formatTtmlTimestamp(word.end)
                builder.append("<span begin=\"$wBegin\" end=\"$wEnd\">${escapeXml(word.text)}</span>")
            }
            builder.append("</p>\n")
        }

        builder.append("    </div>\n  </body>\n</tt>")
        return builder.toString()
    }

    /**
     * 从模型生成指定 LRC 格式
     */
    fun generateLrc(lines: List<LyricsLine>, targetFormat: LyricFormat): String {
        val builder = StringBuilder()

        lines.forEach { line ->
            if (targetFormat == LyricFormat.ENHANCED_LRC) {
                builder.append("[${formatTimestamp(line.start)}] ")
                line.words.forEach { word ->
                    builder.append("<${formatTimestamp(word.start)}>${word.text}")
                }
                if (line.words.isNotEmpty()) {
                    builder.append(" <${formatTimestamp(line.words.last().end)}>")
                }
            } else if (targetFormat == LyricFormat.VERBATIM_LRC) {
                line.words.forEachIndexed { index, word ->
                    builder.append("[${formatTimestamp(word.start)}]${word.text}")
                    if (index == line.words.lastIndex) {
                        builder.append("[${formatTimestamp(word.end)}]")
                    }
                }
            } else { // PLAIN_LRC
                builder.append("[${formatTimestamp(line.start)}]")
                line.words.forEach { word -> builder.append(word.text) }
            }
            builder.append("\n")
        }

        return builder.toString().trim()
    }

    // ==========================================
    // 内部时间与字符串格式化工具
    // ==========================================

    private fun parseLrcTimeMs(matchResult: MatchResult): Long {
        val min = matchResult.groupValues[2].toLong()
        val sec = matchResult.groupValues[3].toLong()
        val ms = matchResult.groupValues[4].padEnd(3, '0').toLong()
        return (min * 60 + sec) * 1000 + ms
    }

    private fun parseTtmlTimeMs(timeStr: String): Long {
        val parts = timeStr.split(":", ".")
        if (parts.size < 4) return 0L
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val ms = parts[3].padEnd(3, '0').toLong()
        return (h * 3600 + m * 60 + s) * 1000 + ms
    }

    @SuppressLint("DefaultLocale")
    private fun formatTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }

    @SuppressLint("DefaultLocale")
    private fun formatTtmlTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}