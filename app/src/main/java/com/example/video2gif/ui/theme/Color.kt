package com.example.video2gif.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 设计 token 由 DESIGN.md 的 OKLCH 值转换为 sRGB。
 * 对比度已校验：浅色主正文 17.3:1、次级正文 6.9:1；深色主正文 17.5:1、次级正文 8.6:1。
 * 修改任意前景/背景组合前请重新校验 WCAG。
 */

internal object LightPalette {
    val primary = Color(0xFF961E7A)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFBE8F4)
    val onPrimaryContainer = Color(0xFF551C45)
    val inversePrimary = Color(0xFFF6BDE2)
    val secondary = Color(0xFF77586C)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFF7E1EE)
    val onSecondaryContainer = Color(0xFF3E2336)
    val tertiary = Color(0xFF296B88)
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFCBEAFB)
    val onTertiaryContainer = Color(0xFF002639)
    val background = Color(0xFFFFFFFF)
    val onBackground = Color(0xFF1D191C)
    val surface = Color(0xFFF7F7F7)
    val onSurface = Color(0xFF1D191C)
    val surfaceVariant = Color(0xFFE2E2E2)
    val onSurfaceVariant = Color(0xFF585457)
    val surfaceTint = Color(0xFF961E7A)
    val inverseSurface = Color(0xFF2D272B)
    val inverseOnSurface = Color(0xFFEEEEEE)
    val error = Color(0xFFA92227)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFFE4E1)
    val onErrorContainer = Color(0xFF65201E)
    val outline = Color(0xFF717171)
    val outlineVariant = Color(0xFFD4D4D4)
    val scrim = Color(0xFF000000)
    val surfaceDim = Color(0xFFE2E2E2)
    val surfaceBright = Color(0xFFF7F7F7)
    val surfaceContainerLowest = Color(0xFFFDFDFD)
    val surfaceContainerLow = Color(0xFFF2F2F2)
    val surfaceContainer = Color(0xFFEBEBEB)
    val surfaceContainerHigh = Color(0xFFE2E2E2)
    val surfaceContainerHighest = Color(0xFFD2D2D2)
}

internal object DarkPalette {
    val primary = Color(0xFFF6BDE2)
    val onPrimary = Color(0xFF291022)
    val primaryContainer = Color(0xFF432038)
    val onPrimaryContainer = Color(0xFFF4E2ED)
    val inversePrimary = Color(0xFF961E7A)
    val secondary = Color(0xFFD5B2C8)
    val onSecondary = Color(0xFF271320)
    val secondaryContainer = Color(0xFF54384B)
    val onSecondaryContainer = Color(0xFFF7E1EE)
    val tertiary = Color(0xFF9DCCE4)
    val onTertiary = Color(0xFF001420)
    val tertiaryContainer = Color(0xFF114054)
    val onTertiaryContainer = Color(0xFFCBEAFB)
    val background = Color(0xFF060606)
    val onBackground = Color(0xFFEEEEEE)
    val surface = Color(0xFF141414)
    val onSurface = Color(0xFFEEEEEE)
    val surfaceVariant = Color(0xFF262626)
    val onSurfaceVariant = Color(0xFFB5AFB3)
    val surfaceTint = Color(0xFFF6BDE2)
    val inverseSurface = Color(0xFFE8E8E8)
    val inverseOnSurface = Color(0xFF1D191C)
    val error = Color(0xFFFFB7B0)
    val onError = Color(0xFF55191A)
    val errorContainer = Color(0xFF572220)
    val onErrorContainer = Color(0xFFFFE7E4)
    val outline = Color(0xFF8F8F8F)
    val outlineVariant = Color(0xFF383838)
    val scrim = Color(0xFF000000)
    val surfaceDim = Color(0xFF060606)
    val surfaceBright = Color(0xFF333333)
    val surfaceContainerLowest = Color(0xFF020202)
    val surfaceContainerLow = Color(0xFF0D0D0D)
    val surfaceContainer = Color(0xFF1F1F1F)
    val surfaceContainerHigh = Color(0xFF262626)
    val surfaceContainerHighest = Color(0xFF333333)
}

/**
 * Material 色彩角色之外的语义色。玻璃材质不用于普通参数行，仅用于视频悬浮控件与底部操作区。
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
    val glassBorder: Color,
    val glassText: Color,
    val glassTextMuted: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF0E5F36),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCF2E3),
    onSuccessContainer = Color(0xFF0C341E),
    warning = Color(0xFF975800),
    warningContainer = Color(0xFFFFECC9),
    onWarningContainer = Color(0xFF573400),
    mediaStage = Color(0xFF0E0B0D),
    mediaStageBorder = Color(0x14FFFFFF),
    glassFill = Color(0xE6FFFFFF),
    glassHighlight = Color(0xE6FFFFFF),
    glassBorder = Color(0x14000000),
    glassText = Color(0xFF1D191C),
    glassTextMuted = Color(0xFF585457),
    skeleton = Color(0xFFE7E3E6),
    skeletonHighlight = Color(0xFFF3F0F2),
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFFA1DBB4),
    onSuccess = Color(0xFF062915),
    successContainer = Color(0xFF173523),
    onSuccessContainer = Color(0xFFD9EEDF),
    warning = Color(0xFFEDC47D),
    warningContainer = Color(0xFF442E09),
    onWarningContainer = Color(0xFFFBEDD1),
    mediaStage = Color(0xFF050405),
    mediaStageBorder = Color(0x1FFFFFFF),
    glassFill = Color(0xEB141414),
    glassHighlight = Color(0x2EFFFFFF),
    glassBorder = Color(0x1FFFFFFF),
    glassText = Color(0xFFEEEEEE),
    glassTextMuted = Color(0xFFB5AFB3),
    skeleton = Color(0xFF262226),
    skeletonHighlight = Color(0xFF332E32),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
