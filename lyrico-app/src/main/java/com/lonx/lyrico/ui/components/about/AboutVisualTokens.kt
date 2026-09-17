package com.lonx.lyrico.ui.components.about

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

/**
 * 关于页毛玻璃的混色参数。
 *
 * 标题用高强度混色让流光透过字形，卡片只做一层柔和的提亮/压暗，
 * 深浅色各一套，避免直接用主题色导致对比度不够。
 */
internal fun aboutTitleBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) {
        listOf(
            BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }

internal fun aboutCardBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) {
        listOf(
            BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }
