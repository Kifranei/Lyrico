package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsWord
import com.lonx.lyrics.model.isWordByWord

object LyricDecoder {
    private val TTML_P_PATTERN = Regex("""<p\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_SPAN_PATTERN = Regex("""<span\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_ROLE_PATTERN = Regex("""ttm:role="([^"]+)"""")

    fun detectFormat(lyricsText: String): LyricFormat? {
        if (lyricsText.isBlank()) return null

        val sampleLines = lyricsText.lines().filter { it.isNotBlank() }

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

    fun decode(lyricsText: String): LyricsResult? {
        val format = detectFormat(lyricsText) ?: return null

        return when (format) {
            LyricFormat.TTML -> parseTtmlToResult(lyricsText)
            LyricFormat.PLAIN_LRC,
            LyricFormat.VERBATIM_LRC,
            LyricFormat.ENHANCED_LRC -> parseLrcToResult(lyricsText)
        }
    }

    private fun parseLrcToResult(lyricsText: String): LyricsResult {
        val allLines = mutableListOf<LyricsLine>()
        val metadataTags = mutableMapOf<String, String>()

        lyricsText.lines().forEach { lineStr ->
            if (lineStr.isBlank()) return@forEach

            val tiMatch = Regex("""^\[ti:(.*)]$""").find(lineStr)
            val arMatch = Regex("""^\[ar:(.*)]$""").find(lineStr)
            val alMatch = Regex("""^\[al:(.*)]$""").find(lineStr)
            when {
                tiMatch != null -> { metadataTags["ti"] = tiMatch.groupValues[1].trim(); return@forEach }
                arMatch != null -> { metadataTags["ar"] = arMatch.groupValues[1].trim(); return@forEach }
                alMatch != null -> { metadataTags["al"] = alMatch.groupValues[1].trim(); return@forEach }
            }

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

                if (text.isNotEmpty() || (i == matches.size - 2 && text.isEmpty())) {
                    val nextTime = if (i + 1 < matches.size) parseLrcTimeMs(matches[i + 1]) else time + 500
                    if (text.isNotEmpty()) {
                        words.add(LyricsWord(start = time, end = nextTime, text = text))
                    }
                }
            }

            if (words.isNotEmpty()) {
                val lineEnd = words.last().end
                allLines.add(LyricsLine(start = lineStart, end = lineEnd, words = words))
            }
        }

        if (allLines.isEmpty()) {
            return LyricsResult(tags = metadataTags, original = emptyList(), translated = null, romanization = null, isWordByWord = false)
        }

        val (original, translated, romanization) = separateLrcTracks(allLines)

        return LyricsResult(
            tags = metadataTags,
            original = original,
            translated = translated,
            romanization = romanization,
            isWordByWord = original.isWordByWord()
        )
    }

    private data class TimeGroup(val time: Long, val lines: MutableList<LyricsLine>)

    private fun separateLrcTracks(allLines: List<LyricsLine>): Triple<List<LyricsLine>, List<LyricsLine>?, List<LyricsLine>?> {
        val groups = mutableListOf<TimeGroup>()
        for (line in allLines) {
            val existing = groups.find { kotlin.math.abs(it.time - line.start) <= 10 }
            if (existing != null) {
                existing.lines.add(line)
            } else {
                groups.add(TimeGroup(line.start, mutableListOf(line)))
            }
        }
        groups.sortBy { it.time }

        var hasMultiple = groups.any { it.lines.size >= 2 }
        if (!hasMultiple) return Triple(allLines, null, null)

        val originalLines = mutableListOf<LyricsLine>()
        val translatedLines = mutableListOf<LyricsLine>()
        val romanizationLines = mutableListOf<LyricsLine>()

        for (group in groups) {
            when {
                group.lines.size >= 3 -> {
                    originalLines.add(group.lines[0])
                    romanizationLines.add(group.lines[1])
                    translatedLines.add(group.lines[2])
                    for (i in 3 until group.lines.size) {
                        translatedLines.add(group.lines[i])
                    }
                }
                group.lines.size == 2 -> {
                    originalLines.add(group.lines[0])
                    translatedLines.add(group.lines[1])
                }
                else -> {
                    originalLines.add(group.lines[0])
                }
            }
        }

        return Triple(originalLines, translatedLines.ifEmpty { null }, romanizationLines.ifEmpty { null })
    }

    private fun parseTtmlToResult(ttmlText: String): LyricsResult {
        val originalLines = mutableListOf<LyricsLine>()
        val translatedLines = mutableListOf<LyricsLine>()
        val romanizationLines = mutableListOf<LyricsLine>()

        TTML_P_PATTERN.findAll(ttmlText).forEach { pMatch ->
            val pBegin = parseTtmlTimeMs(pMatch.groupValues[1])
            val pEnd = parseTtmlTimeMs(pMatch.groupValues[2])
            val innerHtml = pMatch.groupValues[3]

            val roleMatch = TTML_ROLE_PATTERN.find(pMatch.groupValues[0])
            val lineRole = roleMatch?.groupValues?.get(1)

            if (lineRole == "x-translation") {
                val text = LyricFormatter.unescapeXml(innerHtml.replace(Regex("<.*?>"), ""))
                if (text.isNotBlank()) {
                    val words = listOf(LyricsWord(start = pBegin, end = pEnd, text = text))
                    translatedLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
                }
                return@forEach
            }

            if (lineRole == "x-romanization") {
                val text = LyricFormatter.unescapeXml(innerHtml.replace(Regex("<.*?>"), ""))
                if (text.isNotBlank()) {
                    val words = listOf(LyricsWord(start = pBegin, end = pEnd, text = text))
                    romanizationLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
                }
                return@forEach
            }

            val words = mutableListOf<LyricsWord>()
            var innerRomanText: String? = null
            var innerTransText: String? = null

            TTML_SPAN_PATTERN.findAll(innerHtml).forEach { spanMatch ->
                val spanRoleMatch = TTML_ROLE_PATTERN.find(spanMatch.groupValues[0])
                val spanRole = spanRoleMatch?.groupValues?.get(1)

                when (spanRole) {
                    "x-translation" -> {
                        innerTransText = LyricFormatter.unescapeXml(spanMatch.groupValues[3])
                    }
                    "x-romanization" -> {
                        innerRomanText = LyricFormatter.unescapeXml(spanMatch.groupValues[3])
                    }
                    else -> {
                        val sBegin = parseTtmlTimeMs(spanMatch.groupValues[1])
                        val sEnd = parseTtmlTimeMs(spanMatch.groupValues[2])
                        val text = LyricFormatter.unescapeXml(spanMatch.groupValues[3])
                        words.add(LyricsWord(start = sBegin, end = sEnd, text = text))
                    }
                }
            }

            if (words.isNotEmpty()) {
                originalLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
            } else {
                val remainingHtml = innerHtml.replace(Regex("""<span\s+ttm:role="x-(translation|romanization)".*?>.*?</span>"""), "")
                val text = LyricFormatter.unescapeXml(remainingHtml.replace(Regex("<.*?>"), ""))
                if (text.isNotBlank()) {
                    words.add(LyricsWord(start = pBegin, end = pEnd, text = text))
                    originalLines.add(LyricsLine(start = pBegin, end = pEnd, words = words))
                }
            }

            if (!innerRomanText.isNullOrBlank()) {
                val romanWords = listOf(LyricsWord(start = pBegin, end = pEnd, text = innerRomanText))
                romanizationLines.add(LyricsLine(start = pBegin, end = pEnd, words = romanWords))
            }

            if (!innerTransText.isNullOrBlank()) {
                val transWords = listOf(LyricsWord(start = pBegin, end = pEnd, text = innerTransText))
                translatedLines.add(LyricsLine(start = pBegin, end = pEnd, words = transWords))
            }
        }

        return LyricsResult(
            tags = emptyMap(),
            original = originalLines,
            translated = translatedLines.ifEmpty { null },
            romanization = romanizationLines.ifEmpty { null },
            isWordByWord = originalLines.isWordByWord()
        )
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
