package dev.seniorjava.speedy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SpeedyPrimary,
    onPrimary = SpeedyOnPrimary,
    secondary = SpeedySecondary,
    onSecondary = SpeedyOnSecondary,
    tertiary = SpeedyTertiary,
    background = SpeedyBackgroundLight,
    onBackground = SpeedyOnBackgroundLight,
    surface = SpeedySurfaceLight,
    onSurface = SpeedyOnBackgroundLight,
    error = SpeedyError,
    onError = SpeedyOnError,
)

private val DarkColors = darkColorScheme(
    primary = SpeedyPrimary,
    onPrimary = SpeedyOnPrimary,
    secondary = SpeedySecondary,
    onSecondary = SpeedyOnSecondary,
    tertiary = SpeedyTertiary,
    background = SpeedyBackgroundDark,
    onBackground = SpeedyOnBackgroundDark,
    surface = SpeedySurfaceDark,
    onSurface = SpeedyOnBackgroundDark,
    error = SpeedyError,
    onError = SpeedyOnError,
)

@Composable
fun SpeedyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpeedyTypography,
        content = content,
    )
}
