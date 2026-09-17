package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R

/**
 * 关于页流光背景的风格。
 *
 * OS2 和 OS3 是两套不同的着色器（配色、动画方式都不同），
 * OFF 则完全关闭流光，回落到纯色背景。
 * 默认值取决于设备，见 [com.lonx.lyrico.utils.HyperOsDetector.defaultAboutBgEffect]。
 */
enum class AboutBgEffect(
    @field:StringRes val labelRes: Int
) {
    OFF(R.string.about_bg_effect_off),
    OS2(R.string.about_bg_effect_os2),
    OS3(R.string.about_bg_effect_os3);

    /** OS3 着色器与 OS2 着色器的 uniform 不同，绘制层用这个标志区分。 */
    val isOs3: Boolean get() = this == OS3

    companion object {
        /** 宽松解析持久化的枚举名，遇到未知值回退到 [fallback]。 */
        fun fromName(name: String?, fallback: AboutBgEffect): AboutBgEffect =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}
