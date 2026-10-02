package com.athlink.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AthlinkOrange,
    onPrimary = AthlinkWhite,
    primaryContainer = AthlinkOrangeLight,
    onPrimaryContainer = AthlinkDeepBlue,
    secondary = AthlinkDeepBlue,
    onSecondary = AthlinkWhite,
    secondaryContainer = AthlinkBlueLight,
    onSecondaryContainer = AthlinkWhite,
    tertiary = AthlinkGold,
    background = BackgroundLight,
    onBackground = AthlinkBlack,
    surface = SurfaceLight,
    onSurface = AthlinkBlack,
    surfaceVariant = AthlinkLightGray,
    onSurfaceVariant = AthlinkDarkGray,
    error = AthlinkRed,
    outline = AthlinkMedGray
)

private val DarkColorScheme = darkColorScheme(
    primary = AthlinkOrange,
    onPrimary = AthlinkWhite,
    primaryContainer = AthlinkOrangeDark,
    onPrimaryContainer = AthlinkWhite,
    secondary = AthlinkBlueLight,
    onSecondary = AthlinkWhite,
    secondaryContainer = AthlinkBlue,
    onSecondaryContainer = AthlinkOffWhite,
    tertiary = AthlinkGold,
    background = BackgroundDark,
    onBackground = AthlinkOffWhite,
    surface = SurfaceDark,
    onSurface = AthlinkOffWhite,
    surfaceVariant = CardDark,
    onSurfaceVariant = AthlinkMedGray,
    error = AthlinkRed,
    outline = AthlinkBlueLight
)

@Composable
fun AthlinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AthlinkTypography,
        content = content
    )
}
