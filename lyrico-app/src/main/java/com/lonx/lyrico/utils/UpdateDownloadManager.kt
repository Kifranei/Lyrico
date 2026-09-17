package com.lonx.lyrico.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.data.dto.ReleaseApkAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val assetName: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : UpdateDownloadState

    data class Completed(
        val apkFile: File,
        val assetName: String
    ) : UpdateDownloadState

    data class Failed(
        val error: String,
        val assetName: String
    ) : UpdateDownloadState
}

/**
 * 把 Release 里匹配当前 ABI 的 APK 下载到缓存目录，校验后再拉起安装。
 *
 * 下载状态是进程级共享的：离开更新页再回来仍然能看到进度或已下好的包。
 */
object UpdateDownloadManager {
    private const val TAG = "UpdateDownloadManager"

    private const val BUFFER_SIZE = 32 * 1024

    /** 两次进度回调之间的最小间隔，避免刷新过密。 */
    private const val PROGRESS_SAMPLE_INTERVAL_MS = 300L

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /** 重进更新页时恢复“已下载完成”状态，避免重复下载。 */
    fun checkExistingApk(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null
    ): File? {
        val targetFile = File(updatesDir(context), asset.name)
        if (!isApkValid(context, targetFile, expectedVersion)) return null
        _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
        return targetFile
    }

    /** 校验下载下来的确实是本应用、且版本号符合预期的安装包。 */
    fun isApkValid(
        context: Context,
        file: File,
        expectedVersion: String? = null
    ): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        return runCatching {
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: return@runCatching false

            if (archiveInfo.packageName != context.packageName) return@runCatching false
            if (expectedVersion != null) {
                val fileVersion = archiveInfo.versionName?.normalizedVersion()
                val expected = expectedVersion.normalizedVersion()
                if (fileVersion != null && fileVersion != expected) return@runCatching false
            }
            true
        }.getOrDefault(false)
    }

    fun startDownload(
        context: Context,
        asset: ReleaseApkAsset,
        expectedVersion: String? = null
    ) {
        if (_downloadState.value is UpdateDownloadState.Downloading) return

        val updatesDir = updatesDir(context)
        val targetFile = File(updatesDir, asset.name)
        if (isApkValid(context, targetFile, expectedVersion)) {
            _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val tempFile = File(updatesDir, "${asset.name}.downloading")
            try {
                // 只保留本次要下的包，清掉旧版本和残留的临时文件
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name != asset.name && file.name != tempFile.name) {
                        runCatching { file.delete() }
                    }
                }
                if (tempFile.exists()) tempFile.delete()

                _downloadState.value = UpdateDownloadState.Downloading(
                    assetName = asset.name,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = asset.sizeBytes,
                    speedBytesPerSec = 0L
                )

                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("User-Agent", "Lyrico/${BuildConfig.VERSION_NAME}")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body
                    val totalBytes = if (asset.sizeBytes > 0) asset.sizeBytes else body.contentLength()

                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastSampleTime = System.currentTimeMillis()
                    var lastSampleBytes = 0L
                    var speed = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSampleTime
                                if (elapsed < PROGRESS_SAMPLE_INTERVAL_MS) continue

                                val instantSpeed = ((downloadedBytes - lastSampleBytes) * 1000L) / elapsed
                                // 指数平滑，避免速度数字抖得看不清
                                speed = if (speed == 0L) instantSpeed else (speed * 3 + instantSpeed) / 4
                                lastSampleTime = now
                                lastSampleBytes = downloadedBytes

                                _downloadState.value = UpdateDownloadState.Downloading(
                                    assetName = asset.name,
                                    progress = if (totalBytes > 0) {
                                        (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                                    } else {
                                        0f
                                    },
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed
                                )
                            }
                            output.flush()
                        }
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (!isApkValid(context, targetFile, expectedVersion)) {
                    targetFile.delete()
                    error("APK verification failed")
                }

                _downloadState.value = UpdateDownloadState.Completed(targetFile, asset.name)
            } catch (e: Exception) {
                Log.e(TAG, "下载更新包失败", e)
                if (tempFile.exists()) runCatching { tempFile.delete() }
                _downloadState.value = UpdateDownloadState.Failed(
                    error = e.localizedMessage.orEmpty(),
                    assetName = asset.name
                )
            }
        }
    }

    fun reset() {
        downloadJob?.cancel()
        _downloadState.value = UpdateDownloadState.Idle
    }

    /**
     * 拉起系统安装器。没有“安装未知应用”权限时先跳到授权页，
     * 用户授权后回到更新页再点一次即可。
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        runCatching {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val opened = runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            "package:${context.packageName}".toUri()
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                }.getOrDefault(false)
                if (opened) return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { Log.e(TAG, "拉起安装失败", it) }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

    private fun String.normalizedVersion(): String =
        trim().removePrefix("v").removePrefix("V")
}
