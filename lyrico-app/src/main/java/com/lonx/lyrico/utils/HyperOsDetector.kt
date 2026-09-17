package com.lonx.lyrico.utils

import android.os.Build
import com.lonx.lyrico.data.model.AboutBgEffect

/**
 * 从系统属性里识别澎湃 OS 大版本，用来决定关于页流光背景的默认风格。
 */
object HyperOsDetector {

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer in XIAOMI_BRANDS || brand in XIAOMI_BRANDS
    }

    private fun getSystemProperty(key: String): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
        (getMethod.invoke(null, key, "") as? String).orEmpty()
    }.getOrDefault("")

    /**
     * 解析澎湃 OS 大版本号，例如 "OS2.0.x" 返回 2。
     * 非小米设备或读不到版本属性时返回 null。
     */
    fun getHyperOsMajorVersion(): Int? {
        if (!isXiaomiDevice()) return null
        val osName = getSystemProperty("ro.mi.os.version.name")
        val incremental = getSystemProperty("ro.build.version.incremental")
        val raw = osName.ifBlank { incremental }
        if (raw.isBlank()) return null
        return Regex("""OS(\d+)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    /**
     * 澎湃 OS 1 上两套着色器都不是原生观感，默认关闭；
     * 澎湃 OS 2 用 OS2 流光；其余设备（含澎湃 OS 3+）默认 OS3 流光。
     */
    private val defaultBgEffect: AboutBgEffect by lazy {
        when (getHyperOsMajorVersion()) {
            null -> AboutBgEffect.OS3
            1 -> AboutBgEffect.OFF
            2 -> AboutBgEffect.OS2
            else -> AboutBgEffect.OS3
        }
    }

    fun defaultAboutBgEffect(): AboutBgEffect = defaultBgEffect
}
