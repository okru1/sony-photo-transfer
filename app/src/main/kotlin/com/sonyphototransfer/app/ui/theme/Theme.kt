package com.sonyphototransfer.app.ui.theme

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

private val Orange = Color(0xFFFF8A00)

// Dark by default: photos read better on a near-black background, like most photo apps.
private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A1000),
    primaryContainer = Color(0xFF4A2A00),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFD8C3AD),
    background = Color(0xFF101114),
    surface = Color(0xFF101114),
    surfaceVariant = Color(0xFF26272B),
    surfaceContainer = Color(0xFF1B1C20),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8F4E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2E1500),
)

@Composable
fun SonyPhotoTransferTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context).copy(
            primary = Orange,
            background = DarkColors.background,
            surface = DarkColors.surface,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
