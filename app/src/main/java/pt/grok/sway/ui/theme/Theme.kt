package pt.grok.sway.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SwayBlue = Color(0xFF2F95DC)

private val LightColors = lightColorScheme(primary = SwayBlue, secondary = Color(0xFF3A7CA5))
private val DarkColors = darkColorScheme(primary = Color(0xFF66B8F0), secondary = Color(0xFF8CC4E6))

/** Material 3 with the device's dynamic colours where available, Sway blue otherwise. */
@Composable
fun SwayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
