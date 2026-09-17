package com.lonx.lyrico.data.dto

/** Release 里的一个 APK 附件。 */
data class ReleaseApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class ReleaseInfo(
    val versionName: String,
    val releaseNotes: String,
    val url: String,
    /** 发布日期，形如 2026-09-16；取不到时为空串。 */
    val publishedAt: String = "",
    /** Release 里所有的 APK 附件。 */
    val apkAssets: List<ReleaseApkAsset> = emptyList(),
    /** 与当前设备 ABI 匹配的附件，用于直接下载分架构包。 */
    val matchedAsset: ReleaseApkAsset? = null
)
