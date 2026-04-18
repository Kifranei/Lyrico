package com.lonx.lyrico.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

object UriUtils {

    /**
     * 尝试从 SAF 返回的 Tree Uri 中获取绝对路径
     * 仅适用于外部存储设备（手机内置存储和 SD 卡）
     */
    fun getFileAbsolutePath(context: Context, treeUri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val type = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            return if ("primary".equals(type, ignoreCase = true)) {
                val primaryPath = Environment.getExternalStorageDirectory().absolutePath
                if (relativePath.isNotEmpty()) {
                    "$primaryPath/$relativePath"
                } else {
                    primaryPath
                }
            } else {
                val extStoragePaths = getExternalStoragePaths(context)
                for (path in extStoragePaths) {
                    if (path.contains(type)) {
                        return if (relativePath.isNotEmpty()) {
                            "$path/$relativePath"
                        } else {
                            path
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 从 MediaStore URI 获取文件名
     * 适用于 content://media/external/audio/media/xxx 格式的 URI
     */
    fun getMediaStoreFileName(contentResolver: ContentResolver, mediaUri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
            contentResolver.query(
                mediaUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    cursor.getString(displayNameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取所有可能的外部存储路径（包括 SD 卡）
     */
    private fun getExternalStoragePaths(context: Context): List<String> {
        val paths = mutableListOf<String>()
        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (file in externalFilesDirs) {
            if (file != null) {
                val path = file.absolutePath
                if (path.contains("/Android/data")) {
                    paths.add(path.substringBefore("/Android/data"))
                }
            }
        }
        return paths
    }
}