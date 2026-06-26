package com.dhanuk.debtbro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColors = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = BackgroundDark,
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = OnSurfaceDark,
    secondary = PrimaryGreenDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SubtitleGrayDark,
    surfaceTint = PrimaryGreen,
    outline = OutlineDark,
    outlineVariant = DividerDark,
    error = DangerRed,
    onError = BackgroundDark
)

private val LightColors = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = SurfaceLight,
    primaryContainer = SurfaceVariantLight,
    onPrimaryContainer = OnSurfaceLight,
    secondary = PrimaryGreenDark,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = SubtitleGrayLight,
    surfaceTint = BrandPrimaryLight,
    outline = OutlineLight,
    outlineVariant = DividerLight,
    error = DangerRedLight,
    onError = SurfaceLight
)

/** Palette that consumers can read inside @Composable to get theme-aware raw colors. */
data class DebtBroExtraColors(
    val subtitleGray: androidx.compose.ui.graphics.Color,
    val divider: androidx.compose.ui.graphics.Color,
    val cardInner: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val danger: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color
)

val LocalExtraColors = staticCompositionLocalOf {
    // Default to dark scheme colors (matches existing app behavior pre-migration)
    DebtBroExtraColors(
        subtitleGray = SubtitleGrayDark,
        divider = DividerDark,
        cardInner = CardInnerDark,
        success = PrimaryGreen,
        danger = DangerRed,
        warning = WarningAmber
    )
}

@Composable
@ReadOnlyComposable
fun debtBroExtraColors(isDark: Boolean = isSystemInDarkTheme()): DebtBroExtraColors = if (isDark) {
    DebtBroExtraColors(SubtitleGrayDark, DividerDark, CardInnerDark, PrimaryGreen, DangerRed, WarningAmber)
} else {
    DebtBroExtraColors(SubtitleGrayLight, DividerLight, CardInnerLight, BrandPrimaryLight, DangerRedLight, WarningAmber)
}

@Composable
fun DebtBroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extra = debtBroExtraColors(darkTheme)
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = colorScheme, typography = DebtBroTypography, content = content)
    }
}
