package app.harmonium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Deep teal / amber music-player palette (not purple-default)
private val Teal = Color(0xFF7EC8C8)
private val Amber = Color(0xFFE8C27A)
private val Ink = Color(0xFF0B141C)
private val InkElevated = Color(0xFF132231)
private val Mist = Color(0xFFE7EEF2)
private val MistMuted = Color(0xFFB7C5CF)
private val Coral = Color(0xFFD9846A)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    tertiary = Coral,
    background = Ink,
    onBackground = Mist,
    surface = InkElevated,
    onSurface = Mist,
    onSurfaceVariant = MistMuted,
    surfaceVariant = Color(0xFF1A2C3A),
    outline = Color(0xFF3A5160),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F6F),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A16),
    onSecondary = Color.White,
    tertiary = Color(0xFFA3523A),
    background = Color(0xFFF3F7F8),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4A5C68),
    surfaceVariant = Color(0xFFDFE9ED),
    outline = Color(0xFF9AADB8),
)

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun HarmoniumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
