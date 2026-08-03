package app.viora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5555D9),
    secondary = Color(0xFF6B5B95),
    tertiary = Color(0xFF007C78),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC1C1FF),
    secondary = Color(0xFFD3BDF2),
    tertiary = Color(0xFF73D9D2),
)

@Composable
fun VioraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
