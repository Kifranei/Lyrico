package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.internal.MetadataResult
import com.lonx.audiotag.internal.TagLibJNI
import com.lonx.audiotag.model.AudioPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.HashMap
import kotlin.collections.iterator
object AudioTagWriter {
    private const val TAG = "AudioTagWriter"

    suspend fun writeTags(
        pfd: ParcelFileDescriptor,
        updates: Map<String, String>,
        preserveOldTags: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fd = FdUtils.getNativeFd(pfd)

            val finalMap = HashMap<String, MutableList<String>>()

            if (preserveOldTags) {
                (TagLibJNI.read(fd) as? MetadataResult.Success)
                    ?.metadata
                    ?.let { meta ->
                        (meta.id3v2 + meta.xiph + meta.mp4).forEach { (k, v) ->
                            finalMap.getOrPut(k) { mutableListOf() }.addAll(v)
                        }
                    }
            }

            updates.forEach { (k, v) ->
                finalMap[k] = mutableListOf(v)
            }

            val jniMap = HashMap(finalMap.mapValues { it.value.distinct() })

            Log.d(TAG, "Final map to JNI: $jniMap")
            TagLibJNI.write(fd, jniMap)

        } catch (e: Exception) {
            Log.e(TAG, "Write tags error", e)
            false
        }
    }


    suspend fun writePictures(pfd: ParcelFileDescriptor, pictures: List<AudioPicture>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fd = FdUtils.getNativeFd(pfd)
                val data = pictures.map { it.data }.toTypedArray()
                TagLibJNI.writePictures(fd, data)
            } catch (e: Exception) {
                Log.e(TAG, "Write pictures error", e)
                false
            }
        }
    }
}