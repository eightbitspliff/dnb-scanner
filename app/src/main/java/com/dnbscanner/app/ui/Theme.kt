package com.dnbscanner.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Neon-Palette im Rave-/Drum-&-Bass-Look. */
object Neon {
    val Bg = Color(0xFF07080D)
    val Surface = Color(0xFF11131C)
    val SurfaceHi = Color(0xFF1A1D2A)
    val Outline = Color(0xFF2A2E40)
    val Acid = Color(0xFFC6FF00)
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Amber = Color(0xFFFFB300)
    val Text = Color(0xFFF4F6FF)
    val TextDim = Color(0xFF8A8FA8)
    val YouTubeRed = Color(0xFFFF0033)
    val SpotifyGreen = Color(0xFF1ED760)
}

private val Colors = darkColorScheme(
    primary = Neon.Acid,
    onPrimary = Neon.Bg,
    secondary = Neon.Cyan,
    onSecondary = Neon.Bg,
    tertiary = Neon.Magenta,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.SurfaceHi,
    onSurfaceVariant = Neon.TextDim,
    surfaceContainerLowest = Neon.Bg,
    surfaceContainerLow = Neon.Surface,
    surfaceContainer = Neon.Surface,
    surfaceContainerHigh = Neon.SurfaceHi,
    surfaceContainerHighest = Neon.SurfaceHi,
    outline = Neon.Outline,
    outlineVariant = Neon.Outline,
    error = Neon.Amber,
)

private val Type = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun DnBTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
