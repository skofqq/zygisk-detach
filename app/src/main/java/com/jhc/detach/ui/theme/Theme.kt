// Modified by skofqq in 2026: Material 3 Expressive redesign. Original: j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Built-in palette (Material You colors off): tonal spot scheme from the icon blue #0B57D0
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF0040A1),
    inversePrimary = Color(0xFFB2C5FF),
    secondary = Color(0xFF595F72),
    onSecondary = Color(0xFFF9F8FF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF4B5164),
    tertiary = Color(0xFF6B567F),
    onTertiary = Color(0xFFFFF6FF),
    tertiaryContainer = Color(0xFFE8CDFD),
    onTertiaryContainer = Color(0xFF56426A),
    background = Color(0xFFFAF8FE),
    onBackground = Color(0xFF30323B),
    surface = Color(0xFFFAF8FE),
    onSurface = Color(0xFF30323B),
    surfaceVariant = Color(0xFFE1E2ED),
    onSurfaceVariant = Color(0xFF5D5F68),
    surfaceTint = Color(0xFF0B57D0),
    inverseSurface = Color(0xFF0D0E12),
    inverseOnSurface = Color(0xFF9D9CA2),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    outline = Color(0xFF797A84),
    outlineVariant = Color(0xFFB0B1BC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FE),
    surfaceContainer = Color(0xFFEDEDF6),
    surfaceContainerHigh = Color(0xFFE7E7F1),
    surfaceContainerHighest = Color(0xFFE1E2ED),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9D9E4),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB2C5FF),
    onPrimary = Color(0xFF002B72),
    primaryContainer = Color(0xFF0040A1),
    onPrimaryContainer = Color(0xFFDAE2FF),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF3A4052),
    secondaryContainer = Color(0xFF353B4D),
    onSecondaryContainer = Color(0xFFB9BED5),
    tertiary = Color(0xFFF2DEFF),
    onTertiary = Color(0xFF5F4B73),
    tertiaryContainer = Color(0xFFE8CDFD),
    onTertiaryContainer = Color(0xFF56426A),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFE4E5F0),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFE4E5F0),
    surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = Color(0xFFA9AAB5),
    surfaceTint = Color(0xFFB2C5FF),
    inverseSurface = Color(0xFFFAF8FE),
    inverseOnSurface = Color(0xFF545559),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF73757F),
    outlineVariant = Color(0xFF454850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2C34),
    surfaceContainer = Color(0xFF181920),
    surfaceContainerHigh = Color(0xFF1D1F26),
    surfaceContainerHighest = Color(0xFF23262E),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0D0E12),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZygiskdetachTheme(
    darkTheme: Boolean = AppTheme.isDark(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = AppTheme.dynamicColor,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && AppTheme.supportsDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // System bar icon colors follow darkTheme via enableEdgeToEdge() in MainActivity.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}
