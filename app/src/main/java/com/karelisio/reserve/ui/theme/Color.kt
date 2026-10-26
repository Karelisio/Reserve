package com.karelisio.reserve.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** Green fallback scheme, used when dynamic color (Android 12+) is unavailable. */
val GreenLightScheme = lightColorScheme(
    primary = Color(0xFF4C662B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3),
    onPrimaryContainer = Color(0xFF354E16),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7C8),
    onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF1F4E4B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    inverseSurface = Color(0xFF2F312A),
    inverseOnSurface = Color(0xFFF1F2E6),
    inversePrimary = Color(0xFFB1D18A),
    surfaceDim = Color(0xFFDADBD0),
    surfaceBright = Color(0xFFF9FAEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8),
)

val GreenDarkScheme = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = Color(0xFFCDEDA3),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331E),
    secondaryContainer = Color(0xFF404A33),
    onSecondaryContainer = Color(0xFFDCE7C8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    inverseSurface = Color(0xFFE2E3D8),
    inverseOnSurface = Color(0xFF2F312A),
    inversePrimary = Color(0xFF4C662B),
    surfaceDim = Color(0xFF12140E),
    surfaceBright = Color(0xFF383A32),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E),
)

/**
 * Price scale from cheapest (green) to most expensive (red), harmonized with the
 * dynamic primary color so markers blend with the wallpaper palette.
 */
object PriceColors {
    private val cheap = Color(0xFF2E9E4F)
    private val middle = Color(0xFFE0A100)
    private val expensive = Color(0xFFD93A2B)

    /** [fraction] 0 = cheapest, 1 = most expensive. */
    fun scale(fraction: Float, primary: Color, dark: Boolean): Color {
        val f = fraction.coerceIn(0f, 1f)
        val raw = if (f < 0.5f) lerp(cheap, middle, f * 2) else lerp(middle, expensive, (f - 0.5f) * 2)
        val harmonized = harmonize(raw, primary)
        return if (dark) lerp(harmonized, Color.White, 0.18f) else harmonized
    }

    /** Rotates the hue up to 15° toward [source], like MaterialColors.harmonize. */
    fun harmonize(color: Color, source: Color): Color {
        val hsl = FloatArray(3)
        val src = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        ColorUtils.colorToHSL(source.toArgb(), src)
        var diff = src[0] - hsl[0]
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        val rotation = diff.coerceIn(-15f, 15f) * 0.5f
        hsl[0] = (hsl[0] + rotation + 360) % 360
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun onColor(background: Color): Color =
        if (ColorUtils.calculateLuminance(background.toArgb()) > 0.45) Color(0xFF1A1C16) else Color.White
}
