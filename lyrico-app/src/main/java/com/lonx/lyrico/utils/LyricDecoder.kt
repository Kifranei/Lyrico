package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsWord

object LyricDecoder {
    private val TTML_P_PATTERN = Regex("""<p\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_SPAN_PATTERN = Regex("""<span\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)


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

            val matches = LyricFormatter.LRC_TIME_PATTERN.findAll(line).toList()
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
     * 解析 LRC 文本到你自己的 LyricsLine 模型
     */
    fun parseLrcToModels(lyricsText: String): List<LyricsLine> {
        val parsedLines = mutableListOf<LyricsLine>()

        lyricsText.lines().forEach { lineStr ->
            if (lineStr.isBlank() || lineStr.startsWith("[ti:") || lineStr.startsWith("[ar:")) return@forEach

            val matches = LyricFormatter.LRC_TIME_PATTERN.findAll(lineStr).toList()
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
                val text = LyricFormatter.unescapeXml(spanMatch.groupValues[3])
                words.add(LyricsWord(start = sBegin, end = sEnd, text = text))
            }

            if (words.isNotEmpty()) {
                parsedLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
            } else {
                val text = LyricFormatter.unescapeXml(innerHtml.replace(Regex("<.*?>"), ""))
                if (text.isNotBlank()) {
                    words.add(LyricsWord(start = pBegin, end = pEnd, text = text))
                    parsedLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
                }
            }
        }
        return parsedLines
    }


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
}
