package com.lonx.lyrico.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.lonx.lyrico.data.model.SongFile

class MediaScanner(
    private val context: Context,
) {

    private val TAG = "MediaScanner"

    /**
     * 从 MediaStore 同步读取所有音乐文件信息到内存列表。
     *
     * 关键设计：不再使用 Flow+emit 模式。
     * 原因：emit() 是挂起函数，在下游处理每首歌（读取音频标签）期间 Cursor 一直保持打开，
     * 若此时 MediaStore 因文件移动/删除而更新，CursorWindow 内存缓冲会被系统回收或行偏移，
     * 导致 "Couldn't read row X from CursorWindow" 崩溃。
     *
     * 现在改为：在 use {} 块内同步读完所有行到 List，Cursor 立即关闭，
     * 下游处理完全在纯内存数据上操作，与 Cursor 无关。
     */
    fun querySongs(): List<SongFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val results = mutableListOf<SongFile>()

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    try {
                        val filePath = cursor.getString(dataCol)
                        if (filePath.isNullOrBlank()) continue

                        val fileName = cursor.getString(nameCol)
                        if (fileName.isNullOrBlank()) continue

                        val id = cursor.getLong(idCol)

                        results.add(
                            SongFile(
                                mediaId = id,
                                uri = ContentUris.withAppendedId(collection, id),
                                filePath = filePath,
                                fileName = fileName,
                                lastModified = cursor.getLong(modifiedCol) * 1000L,
                                dateAdded = cursor.getLong(addedCol) * 1000L,
                                duration = cursor.getLong(durationCol),
                                fileSize = cursor.getLong(sizeCol)
                            )
                        )
                    } catch (e: IllegalStateException) {
                        // CursorWindow 被系统回收或行偏移，后续行也可能失效，安全退出
                        Log.w(TAG, "CursorWindow 异常: ${e.message}，已读取 ${results.size} 条，停止读取")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "读取行异常: ${e.message}，跳过")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询 MediaStore 异常", e)
        }

        Log.d(TAG, "从 MediaStore 读取到 ${results.size} 首音乐文件")
        return results
    }
}