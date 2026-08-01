package pl.godzinypracy.workly.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF82F8D2),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    secondaryContainer = Color(0xFFCDE9DD),
    onSecondaryContainer = Color(0xFF082019),
    tertiary = Color(0xFF426277),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceVariant = Color(0xFFDDE5E1),
    outline = Color(0xFF6F7975)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF35D0A0),
    onPrimary = Color(0xFF002118),
    primaryContainer = Color(0xFF123D34),
    onPrimaryContainer = Color(0xFF9AF5D5),
    secondary = Color(0xFFA9CFC3),
    secondaryContainer = Color(0xFF263C36),
    onSecondaryContainer = Color(0xFFD0EDE3),
    tertiary = Color(0xFF8CB9FF),
    background = Color(0xFF0B1220),
    surface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFF26313A),
    outline = Color(0xFF82918C)
)

@Composable
fun WorklyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        darkTheme -> ProfessionalDarkColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
