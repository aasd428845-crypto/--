package com.example.ui.theme

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

// NOAEM Cleaning Services Brand Colors
private val NawaemPrimary = Color(0xFF00BCD4) // Bright Teal
private val NawaemPrimaryDark = Color(0xFF0097A7) // Dark Teal
private val NawaemSecondary = Color(0xFF0F2849) // Dark Navy
private val NawaemSecondaryDark = Color(0xFF00BCD4) // Bright Teal
private val NawaemBackground = Color(0xFFFAFAFA) // Off-white
private val NawaemBackgroundDark = Color(0xFF121212) // Dark background
private val NawaemSurface = Color(0xFFFFFFFF) // White
private val NawaemSurfaceDark = Color(0xFF1E1E1E) // Dark surface

private val DarkColorScheme =
  darkColorScheme(
    primary = NawaemPrimaryDark,
    secondary = NawaemSecondaryDark,
    background = NawaemBackgroundDark,
    surface = NawaemSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    outline = Color(0xFF37474F),
    primaryContainer = Color(0xFF00ACC1),
    onPrimaryContainer = Color(0xFF0F2849)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NawaemPrimary,
    secondary = NawaemSecondary,
    background = NawaemBackground,
    surface = NawaemSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F2849),
    onSurface = Color(0xFF0F2849),
    outline = Color(0xFFBDBDBD),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF0097A7)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // White Light mode default branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = {
      androidx.compose.runtime.CompositionLocalProvider(
        LocalContext provides LocalContext.current
      ) {
        content()
      }
    }
  )
}
