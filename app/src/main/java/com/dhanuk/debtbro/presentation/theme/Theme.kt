package com.dhanuk.debtbro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(primary = PrimaryGreen, onPrimary = BackgroundDark, background = BackgroundDark, surface = SurfaceDark, onBackground = OnSurfaceDark, onSurface = OnSurfaceDark, error = DangerRed)
private val LightColors = lightColorScheme(primary = PrimaryGreenLight, onPrimary = Color.White, background = Color(0xFFF5F5F5), surface = Color.White, onBackground = Color(0xFF0D0D0D), onSurface = Color(0xFF0D0D0D), error = DangerRedLight)

@Composable
fun DebtBroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = DebtBroTypography, content = content)
}
