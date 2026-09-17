package com.lonx.lyrico.utils

import android.os.Build
import com.lonx.lyrico.data.dto.ReleaseApkAsset

/**
 * 按设备 ABI 在 Release 附件里挑出该装哪个分架构包。
 *
 * 应用开启了 ABI 分包（arm64-v8a / armeabi-v7a），所以一次 Release 会带多个 APK，
 * 文件名里才有架构标记。挑选顺序：设备支持的 ABI（按优先级）→ 通用包 → 不带架构标记的包 → 第一个。
 */
object ReleaseAssetMatcher {

    fun matchForDevice(
        assets: List<ReleaseApkAsset>,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.orEmpty().toList()
    ): ReleaseApkAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.size <= 1) return apkAssets.firstOrNull()

        for (abi in supportedAbis) {
            apkAssets.firstOrNull { matchesAbi(it.name.lowercase(), abi.lowercase()) }
                ?.let { return it }
        }

        apkAssets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("universal") || name.contains("fat")
        }?.let { return it }

        apkAssets.firstOrNull { detectArchLabel(it.name) == null }?.let { return it }

        return apkAssets.first()
    }

    private fun matchesAbi(name: String, abi: String): Boolean = when (abi) {
        "arm64-v8a" -> !name.contains("v7a") &&
            (name.contains("arm64") || name.contains("aarch64") || name.contains("v8a"))

        "armeabi-v7a" -> !name.contains("arm64") && !name.contains("v8a") &&
            (name.contains("armeabi-v7a") || name.contains("armv7") || name.contains("v7a"))

        "armeabi" -> name.contains("armeabi") &&
            !name.contains("v7a") && !name.contains("v8a") && !name.contains("arm64")

        "x86_64" -> name.contains("x86_64") || name.contains("x64")

        "x86" -> name.contains("x86") && !name.contains("x86_64") && !name.contains("x64")

        else -> name.contains(abi)
    }

    /** 从文件名里读出架构标签，用于在更新页上展示“这次装的是哪个包”。 */
    fun detectArchLabel(assetName: String): String? {
        val name = assetName.lowercase()
        return when {
            name.contains("arm64") || name.contains("aarch64") || name.contains("v8a") -> "arm64-v8a"
            name.contains("armeabi-v7a") || name.contains("armv7") || name.contains("v7a") -> "armeabi-v7a"
            name.contains("x86_64") || name.contains("x64") -> "x86_64"
            name.contains("x86") -> "x86"
            name.contains("universal") -> "universal"
            else -> null
        }
    }
}
