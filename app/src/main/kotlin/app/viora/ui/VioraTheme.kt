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

val VioraMint = Color(0xFF79E2C2)
val VioraBlue = Color(0xFF8EBBFF)
val VioraCoral = Color(0xFFFF8E8E)
val VioraAmber = Color(0xFFFFCA72)
val VioraCanvas = Color(0xFF080A0F)
val VioraSurface = Color(0xFF11151D)
val VioraSurfaceHigh = Color(0xFF181E29)

private val VioraColors = darkColorScheme(
    primary = VioraMint,
    onPrimary = Color(0xFF002119),
    primaryContainer = Color(0xFF123D33),
    onPrimaryContainer = Color(0xFFB7F5E2),
    secondary = VioraBlue,
    onSecondary = Color(0xFF061D38),
    secondaryContainer = Color(0xFF182C47),
    onSecondaryContainer = Color(0xFFD5E5FF),
    tertiary = VioraAmber,
    error = VioraCoral,
    errorContainer = Color(0xFF481E23),
    background = VioraCanvas,
    onBackground = Color(0xFFF2F5FA),
    surface = VioraSurface,
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = VioraSurfaceHigh,
    onSurfaceVariant = Color(0xFFA9B1BF),
    outline = Color(0xFF303846),
    outlineVariant = Color(0xFF202631),
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
