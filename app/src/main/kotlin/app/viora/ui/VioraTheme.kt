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

val VioraIce = Color(0xFFF4F5F9)
val VioraBlue = Color(0xFF9BAAFD)
val VioraSuccess = Color(0xFF57C785)
val VioraCoral = Color(0xFFFF8E8E)
val VioraAmber = Color(0xFFF0B66A)
val VioraCanvas = Color(0xFF0D0F14)
val VioraSurface = Color(0xFF16181F)
val VioraSurfaceHigh = Color(0xFF20232C)

private val VioraColors = darkColorScheme(
    primary = VioraBlue,
    onPrimary = Color(0xFF12141C),
    primaryContainer = Color(0xFF2A3168),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = VioraIce,
    onSecondary = Color(0xFF151820),
    secondaryContainer = Color(0xFF252A38),
    onSecondaryContainer = Color(0xFFDDE2F2),
    tertiary = VioraAmber,
    error = VioraCoral,
    errorContainer = Color(0xFF4A2428),
    background = VioraCanvas,
    onBackground = Color(0xFFF4F5F9),
    surface = VioraSurface,
    onSurface = Color(0xFFF4F5F9),
    surfaceVariant = VioraSurfaceHigh,
    onSurfaceVariant = Color(0xFFB4BAC9),
    outline = Color(0xFF596174),
    outlineVariant = Color(0xFF303642),
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
