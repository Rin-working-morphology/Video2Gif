package com.example.video2gif.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.video2gif.domain.ThemeMode

private val LightScheme = lightColorScheme(
    primary = LightPalette.primary,
    onPrimary = LightPalette.onPrimary,
    primaryContainer = LightPalette.primaryContainer,
    onPrimaryContainer = LightPalette.onPrimaryContainer,
    inversePrimary = LightPalette.inversePrimary,
    secondary = LightPalette.secondary,
    onSecondary = LightPalette.onSecondary,
    secondaryContainer = LightPalette.secondaryContainer,
    onSecondaryContainer = LightPalette.onSecondaryContainer,
    tertiary = LightPalette.tertiary,
    onTertiary = LightPalette.onTertiary,
    tertiaryContainer = LightPalette.tertiaryContainer,
    onTertiaryContainer = LightPalette.onTertiaryContainer,
    background = LightPalette.background,
    onBackground = LightPalette.onBackground,
    surface = LightPalette.surface,
    onSurface = LightPalette.onSurface,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceVariant,
    surfaceTint = LightPalette.surfaceTint,
    inverseSurface = LightPalette.inverseSurface,
    inverseOnSurface = LightPalette.inverseOnSurface,
    error = LightPalette.error,
    onError = LightPalette.onError,
    errorContainer = LightPalette.errorContainer,
    onErrorContainer = LightPalette.onErrorContainer,
    outline = LightPalette.outline,
    outlineVariant = LightPalette.outlineVariant,
    scrim = LightPalette.scrim,
    surfaceDim = LightPalette.surfaceDim,
    surfaceBright = LightPalette.surfaceBright,
    surfaceContainerLowest = LightPalette.surfaceContainerLowest,
    surfaceContainerLow = LightPalette.surfaceContainerLow,
    surfaceContainer = LightPalette.surfaceContainer,
    surfaceContainerHigh = LightPalette.surfaceContainerHigh,
    surfaceContainerHighest = LightPalette.surfaceContainerHighest,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.primary,
    onPrimary = DarkPalette.onPrimary,
    primaryContainer = DarkPalette.primaryContainer,
    onPrimaryContainer = DarkPalette.onPrimaryContainer,
    inversePrimary = DarkPalette.inversePrimary,
    secondary = DarkPalette.secondary,
    onSecondary = DarkPalette.onSecondary,
    secondaryContainer = DarkPalette.secondaryContainer,
    onSecondaryContainer = DarkPalette.onSecondaryContainer,
    tertiary = DarkPalette.tertiary,
    onTertiary = DarkPalette.onTertiary,
    tertiaryContainer = DarkPalette.tertiaryContainer,
    onTertiaryContainer = DarkPalette.onTertiaryContainer,
    background = DarkPalette.background,
    onBackground = DarkPalette.onBackground,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceVariant,
    surfaceTint = DarkPalette.surfaceTint,
    inverseSurface = DarkPalette.inverseSurface,
    inverseOnSurface = DarkPalette.inverseOnSurface,
    error = DarkPalette.error,
    onError = DarkPalette.onError,
    errorContainer = DarkPalette.errorContainer,
    onErrorContainer = DarkPalette.onErrorContainer,
    outline = DarkPalette.outline,
    outlineVariant = DarkPalette.outlineVariant,
    scrim = DarkPalette.scrim,
    surfaceDim = DarkPalette.surfaceDim,
    surfaceBright = DarkPalette.surfaceBright,
    surfaceContainerLowest = DarkPalette.surfaceContainerLowest,
    surfaceContainerLow = DarkPalette.surfaceContainerLow,
    surfaceContainer = DarkPalette.surfaceContainer,
    surfaceContainerHigh = DarkPalette.surfaceContainerHigh,
    surfaceContainerHighest = DarkPalette.surfaceContainerHighest,
)

@Composable
fun Video2GifTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
        ) {
            // MaterialTheme 本身不设置默认内容色；没有 Surface 包裹的 Text 会落到黑色，
            // 在深色主题下不可读。这里统一提供 onBackground。
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                content()
            }
        }
    }
}

/** 主题内的玻璃与语义扩展色。 */
object AppTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current

    /** 黑底媒体舞台上始终使用的深色遮罩，避免视频内容与系统栏互相干扰。 */
    val mediaScrim = Color(0xFF000000)
}
