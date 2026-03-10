package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.internal.Metadata
import com.lonx.audiotag.internal.MetadataResult
import com.lonx.audiotag.internal.TagLibJNI
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTagReader {

    private const val TAG = "AudioTagReader"

    suspend fun read(pfd: ParcelFileDescriptor, readPictures: Boolean = true): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {


                val fd = FdUtils.getNativeFd(pfd)

                val result = TagLibJNI.read(fd)
                val metadata = (result as? MetadataResult.Success)?.metadata ?: return@withContext AudioTagData()

                buildAudioTagData(metadata, readPictures)

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                AudioTagData()
            }
        }
    }
    suspend fun readPicture(pfd: ParcelFileDescriptor): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val fd = FdUtils.getNativeFd(pfd)
                val result = TagLibJNI.readPicture(fd)
                return@withContext result

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
            }
            return@withContext null
        }
    }

    private fun buildAudioTagData(metadata: Metadata, readPictures: Boolean): AudioTagData {
        // 合并所有 tag map
        val props = LinkedHashMap<String, List<String>>().apply {
            putAll(metadata.xiph)
            putAll(metadata.mp4)
            putAll(metadata.id3v2)
        }

        Log.d(TAG, "Reading tags: $props")
        fun getFirst(vararg keys: String): String? =
            keys.asSequence()
                .mapNotNull { props[it]?.firstOrNull()?.trim() }
                .firstOrNull { it.isNotEmpty() }

        fun getInt(vararg keys: String): Int? =
            getFirst(*keys)
                ?.substringBefore("/")
                ?.toIntOrNull()

        val audioProps = metadata.properties

        Log.d(TAG, "Reading properties: $audioProps")
        // 返回 AudioTagData
        return AudioTagData(
            title = getFirst("TIT2", "©nam", "TITLE", "sonm"),

            artist = getFirst("TPE1", "©ART", "ARTIST", "----:COM.APPLE.ITUNES:PERFORMER"),

            album = getFirst("TALB", "©alb", "ALBUM"),

            albumArtist = getFirst("TPE2", "aART", "ALBUMARTIST", "soaa"),

            date = getFirst("TDRC", "TDRL", "©day", "DATE", "----:COM.APPLE.ITUNES:RELEASETIME"),

            genre = getFirst("TCON", "©gen", "GENRE"),

            trackNumber = getFirst("TRCK", "trkn", "TRACKNUMBER", "©trk"),

            discNumber = getInt("TPOS", "disk", "DISCNUMBER"),

            composer = getFirst("TCOM", "©wrt", "COMPOSER", "soco"),

            lyricist = getFirst("TEXT", "LYRICIST", "----:COM.APPLE.ITUNES:LYRICIST"),

            lyrics = getFirst("USLT", "©lyr", "LYRICS", "UNSYNCEDLYRICS"),

            comment = getFirst("COMM", "©cmt", "COMMENT"),

            durationMilliseconds = metadata.properties.durationMs.toInt(),
            bitrate = metadata.properties.bitrateKbps,
            sampleRate = metadata.properties.sampleRateHz,
            channels = metadata.properties.channels,

            pictures = if (readPictures && metadata.cover != null) {
                listOf(AudioPicture(data = metadata.cover, mimeType = "image/*", pictureType = "3"))
            } else emptyList()
        )
    }
}