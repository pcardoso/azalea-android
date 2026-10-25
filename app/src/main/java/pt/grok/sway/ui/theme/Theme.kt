package pt.grok.sway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sway blue, the same accent as the iOS app (AccentColor) and the web app.
private val SwayBlue = Color(0xFF2F95DC)
private val SwayBlueDark = Color(0xFF66B8F0)

private val LightColors = lightColorScheme(
    primary = SwayBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8F8),
    onPrimaryContainer = Color(0xFF0B3A5C),
    secondary = Color(0xFF3A7CA5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBF5),
    onSecondaryContainer = Color(0xFF12364D),
    tertiary = Color(0xFFE08A00),
    tertiaryContainer = Color(0xFFFFE3BF),
    onTertiaryContainer = Color(0xFF4A2D00),
    // Neutral greys instead of Material's default lavender-tinted surfaces.
    background = Color(0xFFF8F9FB),
    surface = Color(0xFFF8F9FB),
    surfaceVariant = Color(0xFFE1E4E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFECEFF3),
    surfaceContainerHigh = Color(0xFFE6E9EE),
    surfaceContainerHighest = Color(0xFFE0E3E8),
)

private val DarkColors = darkColorScheme(
    primary = SwayBlueDark,
    onPrimary = Color(0xFF00324F),
    primaryContainer = Color(0xFF15527D),
    onPrimaryContainer = Color(0xFFD3E8F8),
    secondary = Color(0xFF8CC4E6),
    onSecondary = Color(0xFF0E3349),
    secondaryContainer = Color(0xFF2A5470),
    onSecondaryContainer = Color(0xFFDCEBF5),
    tertiary = Color(0xFFFFB955),
    tertiaryContainer = Color(0xFF6B4300),
    onTertiaryContainer = Color(0xFFFFE3BF),
    background = Color(0xFF111316),
    surface = Color(0xFF111316),
    surfaceVariant = Color(0xFF40454C),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)

/** Material 3 with Sway blue everywhere (no wallpaper-based dynamic colour), so it matches iOS and web. */
@Composable
fun SwayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
