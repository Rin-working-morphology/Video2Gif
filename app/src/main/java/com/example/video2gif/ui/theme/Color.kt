package com.example.video2gif.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/*
 * 设计 token 由 DESIGN.md 的 OKLCH 值转换为 sRGB。
 *
 * 主题：「极光石墨」——近黑石墨底 + 薄荷极光。彩色区域只来自极光、选中态与主操作，
 * 其余表面保持低饱和的青灰中性色，避免整屏发光。
 *
 * 对比度已校验（WCAG 2.1）：
 *   深色 主正文 16.5:1 / 次级正文 9.2:1 / 主按钮文字 12.7:1 / 错误文字 8.0:1
 *   浅色 主正文 17.1:1 / 次级正文 8.2:1 / 主按钮文字 5.8:1 / 错误文字 5.7:1
 * 修改任意前景/背景组合前请重新校验。
 */

internal object LightPalette {
    val primary = Color(0xFF007167)
    val onPrimary = Color(0xFFFDFDFD)
    val primaryContainer = Color(0xFFC6EEE6)
    val onPrimaryContainer = Color(0xFF003632)
    val inversePrimary = Color(0xFF74EED2)
    val secondary = Color(0xFF3C6377)
    val onSecondary = Color(0xFFFDFDFD)
    val secondaryContainer = Color(0xFFD1E8F2)
    val onSecondaryContainer = Color(0xFF18313D)
    val tertiary = Color(0xFF205F73)
    val onTertiary = Color(0xFFFDFDFD)
    val tertiaryContainer = Color(0xFFC7E6EF)
    val onTertiaryContainer = Color(0xFF0A2F3A)
    val background = Color(0xFFF5FCFB)
    val onBackground = Color(0xFF0E191B)
    val surface = Color(0xFFEFF8F7)
    val onSurface = Color(0xFF0E191B)
    val surfaceVariant = Color(0xFFDFEBEA)
    val onSurfaceVariant = Color(0xFF424E50)
    val surfaceTint = Color(0xFF007167)
    val inverseSurface = Color(0xFF141E20)
    val inverseOnSurface = Color(0xFFEFF5F5)
    val error = Color(0xFFB33834)
    val onError = Color(0xFFFDFDFD)
    val errorContainer = Color(0xFFFFDFDD)
    val onErrorContainer = Color(0xFF5F1A1A)
    val outline = Color(0xFF6B7778)
    val outlineVariant = Color(0xFFCFDADA)
    val scrim = Color(0xFF000000)
    val surfaceDim = Color(0xFFCAD7D6)
    val surfaceBright = Color(0xFFFCFFFF)
    val surfaceContainerLowest = Color(0xFFFCFFFF)
    val surfaceContainerLow = Color(0xFFE8F1F1)
    val surfaceContainer = Color(0xFFDFEBEA)
    val surfaceContainerHigh = Color(0xFFD5E2E1)
    val surfaceContainerHighest = Color(0xFFCAD7D6)
}

internal object DarkPalette {
    val primary = Color(0xFF74EED2)
    val onPrimary = Color(0xFF021C1B)
    val primaryContainer = Color(0xFF0D3F3B)
    val onPrimaryContainer = Color(0xFFCEF7EC)
    val inversePrimary = Color(0xFF007167)
    val secondary = Color(0xFF93B8C8)
    val onSecondary = Color(0xFF0A161B)
    val secondaryContainer = Color(0xFF1D313A)
    val onSecondaryContainer = Color(0xFFDDEBEF)
    val tertiary = Color(0xFF82D1DA)
    val onTertiary = Color(0xFF05171A)
    val tertiaryContainer = Color(0xFF11393F)
    val onTertiaryContainer = Color(0xFFD2EEF0)
    val background = Color(0xFF0E1718)
    val onBackground = Color(0xFFEFF5F5)
    val surface = Color(0xFF141E20)
    val onSurface = Color(0xFFEFF5F5)
    val surfaceVariant = Color(0xFF1C282A)
    val onSurfaceVariant = Color(0xFFAEBBBC)
    val surfaceTint = Color(0xFF74EED2)
    val inverseSurface = Color(0xFFE8F1F1)
    val inverseOnSurface = Color(0xFF141E20)
    val error = Color(0xFFF98E8B)
    val onError = Color(0xFF321111)
    val errorContainer = Color(0xFF532121)
    val onErrorContainer = Color(0xFFFFDEDB)
    val outline = Color(0xFF707F81)
    val outlineVariant = Color(0xFF2B3537)
    val scrim = Color(0xFF000000)
    val surfaceDim = Color(0xFF0A1112)
    val surfaceBright = Color(0xFF2A383B)
    val surfaceContainerLowest = Color(0xFF0A1112)
    val surfaceContainerLow = Color(0xFF162124)
    val surfaceContainer = Color(0xFF1C282A)
    val surfaceContainerHigh = Color(0xFF222F32)
    val surfaceContainerHighest = Color(0xFF2A383B)
}

/**
 * Material 色彩角色之外的语义色。
 *
 * - mediaStage：媒体舞台衬底，任何视频都能在其上读出边界。
 * - glass*：只用于悬浮在内容之上的操作区与预览工具条。
 * - aurora、ornament、film、sparkle：界面氛围层的绘制颜色，不用于文字。
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val mediaStage: Color,
    val mediaStageBorder: Color,
    val glassFill: Color,
    val glassHighlight: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassText: Color,
    val glassTextMuted: Color,
    val auroraCrest: Color,
    val auroraCore: Color,
    val auroraDeep: Color,
    val auroraPale: Color,
    val glow: Color,
    val ornament: Color,
    val sparkle: Color,
    val filmA: Color,
    val filmB: Color,
    val filmC: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF076246),
    onSuccess = Color(0xFFFDFDFD),
    successContainer = Color(0xFFCFEFE0),
    onSuccessContainer = Color(0xFF052E22),
    warning = Color(0xFF8A5F28),
    warningContainer = Color(0xFFFBE7C9),
    onWarningContainer = Color(0xFF462D0B),
    mediaStage = Color(0xFF08191B),
    mediaStageBorder = Color(0x14000000),
    glassFill = Color(0xE8FCFFFF),
    glassHighlight = Color(0x66FFFFFF),
    glassTint = Color(0x1C007167),
    glassBorder = Color(0x3A0E191B),
    glassText = Color(0xFF0E191B),
    glassTextMuted = Color(0xFF424E50),
    auroraCrest = Color(0xFF4FC0B0),
    auroraCore = Color(0xFF379E95),
    auroraDeep = Color(0xFF175E66),
    auroraPale = Color(0xFF9AD6C8),
    glow = Color(0xFF007167),
    ornament = Color(0xFF223132),
    sparkle = Color(0xFF25786C),
    filmA = Color(0xFFB9CFD1),
    filmB = Color(0xFFCBDEE0),
    filmC = Color(0xFFA5C4C7),
    skeleton = Color(0xFFDCE9E8),
    skeletonHighlight = Color(0xFFF2FAF9),
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF8EDAB9),
    onSuccess = Color(0xFF071A13),
    successContainer = Color(0xFF18342A),
    onSuccessContainer = Color(0xFFCEEBDE),
    warning = Color(0xFFEFC584),
    warningContainer = Color(0xFF463117),
    onWarningContainer = Color(0xFFF8E9D2),
    mediaStage = Color(0xFF03090A),
    mediaStageBorder = Color(0x1FFFFFFF),
    glassFill = Color(0xCC0C1618),
    glassHighlight = Color(0x3DFFFFFF),
    glassTint = Color(0x2674EED2),
    glassBorder = Color(0x40FFFFFF),
    glassText = Color(0xFFEFF5F5),
    glassTextMuted = Color(0xFFAEBBBC),
    auroraCrest = Color(0xFF4CC8B6),
    auroraCore = Color(0xFF33A096),
    auroraDeep = Color(0xFF14606A),
    auroraPale = Color(0xFF96D2C4),
    glow = Color(0xFF2FA89B),
    ornament = Color(0xFFEBF4F3),
    sparkle = Color(0xFFF0FEF9),
    filmA = Color(0xFF2A4246),
    filmB = Color(0xFF1C3233),
    filmC = Color(0xFF345758),
    skeleton = Color(0xFF1F2C2F),
    skeletonHighlight = Color(0xFF2C3D40),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * 动态取色（Android 12+）开启时，极光层跟随壁纸主色偏移，避免按钮换了色系而背景仍是薄荷绿。
 * 混合比例偏保守：保留品牌氛围，只让色相靠向壁纸。
 */
fun ExtendedColors.harmonizeWith(accent: Color): ExtendedColors = copy(
    auroraCrest = lerp(accent, auroraCrest, 0.55f),
    auroraCore = lerp(accent, auroraCore, 0.45f),
    auroraDeep = lerp(accent, auroraDeep, 0.35f),
    auroraPale = lerp(accent, auroraPale, 0.55f),
    glow = lerp(accent, glow, 0.6f),
    glassTint = lerp(accent, glassTint, 0.25f).copy(alpha = glassTint.alpha),
    sparkle = lerp(accent, sparkle, 0.45f),
)
