package com.jaagrit.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalAppLanguage = compositionLocalOf { "hi" }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9EE45), // HorizonLime
    onPrimary = Color(0xFF18392B), // HorizonForest
    primaryContainer = Color(0xFF224D3B),
    onPrimaryContainer = Color(0xFFD6FF85),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF003912),
    secondaryContainer = Color(0xFF1E3926),
    onSecondaryContainer = Color(0xFFA5D6A7),
    tertiary = Color(0xFFFFB74D), // HorizonAmber
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF101813),
    onBackground = Color(0xFFE2E9E2),
    surface = Color(0xFF15221B),
    onSurface = Color(0xFFE2E9E2),
    surfaceVariant = Color(0xFF1E2F26),
    onSurfaceVariant = Color(0xFFC2CEC5),
    outline = Color(0xFF8B998F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF18392B), // HorizonForest
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F2A3),
    onPrimaryContainer = Color(0xFF0F261C),
    secondary = Color(0xFF2E6B48),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDEFD0),
    onSecondaryContainer = Color(0xFF0A2B18),
    tertiary = Color(0xFF8C5300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF9F7F1),
    onBackground = Color(0xFF18201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18201B),
    surfaceVariant = Color(0xFFE8E4D9),
    onSurfaceVariant = Color(0xFF424A43),
    outline = Color(0xFF727B73),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun JaagritTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    isHindi: Boolean = LocalAppLanguage.current != "en",
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val typography = remember(isHindi) {
        buildJaagritTypography(isHindi = isHindi)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}