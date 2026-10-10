package com.mirandadevsource.exquisssitaappmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = BrandyBrown,
    onPrimary = Color.White,
    secondary = ApricotIllusion,
    onSecondary = ChocolateFondant,
    tertiary = OfflineAmber,
    onTertiary = Color.White,
    background = VanillaCustard,
    onBackground = ChocolateFondant,
    surface = VanillaCustard,
    onSurface = ChocolateFondant,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun ExquisssitaAppManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}