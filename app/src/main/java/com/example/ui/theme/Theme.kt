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
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFFBDBDBD),
    primaryContainer = NawaemPrimaryLight,
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
