package com.edgeflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Brand palette.
 *
 * A cool blue seed with a cyan accent, chosen so the accent stays legible on
 * both the light and the dark surface. The neutral ramp is deliberately
 * blue-tinted rather than pure grey: a fully desaturated surface next to a
 * saturated accent reads as muddy on OLED panels.
 */
private val BrandBlue = Color(0xFF2563EB)
private val BrandCyan = Color(0xFF06B6D4)
private val BrandAmber = Color(0xFFF59E0B)
private val BrandRose = Color(0xFFE11D48)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color(0xFFDCE6FF),

    secondary = Color(0xFF67D7EE),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0E7490),
    onSecondaryContainer = Color(0xFFCFF5FF),

    tertiary = Color(0xFFF7C566),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFFB45309),
    onTertiaryContainer = Color(0xFFFFEBC7),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFC3CAD8),

    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF101A2B),
    surfaceContainer = Color(0xFF16223A),
    surfaceContainerHigh = Color(0xFF1D2B45),
    surfaceContainerHighest = Color(0xFF243352),

    outline = Color(0xFF8C95A8),
    outlineVariant = Color(0xFF3A4459),
)

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001945),

    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5FF),
    onSecondaryContainer = Color(0xFF00363F),

    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC7),
    onTertiaryContainer = Color(0xFF3F2E00),

    error = BrandRose,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF444C5C),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEFF6),
    surfaceContainerHigh = Color(0xFFE4E9F2),
    surfaceContainerHighest = Color(0xFFDDE3ED),

    outline = Color(0xFF747C8C),
    outlineVariant = Color(0xFFC4CAD6),
)

/**
 * Applies the EdgeFlow theme.
 *
 * @param dynamicColor when true and the device supports it, derives the palette
 *                     from the user's wallpaper. Defaults to on for Android 12+
 *                     because a settings app that ignores the system palette
 *                     looks foreign on a themed device; the brand palette is
 *                     the fallback everywhere else.
 */
@Composable
fun EdgeFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EdgeFlowTypography,
        shapes = EdgeFlowShapes,
        content = content,
    )
}
