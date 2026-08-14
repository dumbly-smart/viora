package app.viora.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VioraIce = Color(0xFFF1F1F1)
val VioraBlue = Color(0xFFD7D7D7)
val VioraSuccess = Color(0xFFE6E6E6)
val VioraCoral = Color(0xFFB8B8B8)
val VioraAmber = Color(0xFF989898)
val VioraCanvas = Color(0xFF09090B)
val VioraSurface = Color(0xFF121212)
val VioraSurfaceHigh = Color(0xFF1B1B1B)

private val VioraColors = darkColorScheme(
    primary = VioraIce,
    onPrimary = Color(0xFF151515),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = VioraBlue,
    onSecondary = Color(0xFF191919),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = VioraAmber,
    error = VioraCoral,
    errorContainer = Color(0xFF343434),
    background = VioraCanvas,
    onBackground = Color(0xFFF3F3F3),
    surface = VioraSurface,
    onSurface = Color(0xFFF3F3F3),
    surfaceVariant = VioraSurfaceHigh,
    onSurfaceVariant = Color(0xFFB2B2B2),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2D2D2D),
)

private val VioraTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
)

private val VioraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun VioraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VioraColors,
        typography = VioraTypography,
        shapes = VioraShapes,
        content = content,
    )
}
