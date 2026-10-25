package com.vortexdl.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Indigo600 = Color(0xFF4F46E5)
private val Indigo700 = Color(0xFF4338CA)
private val Indigo200 = Color(0xFFA5B4FC)
private val Indigo900 = Color(0xFF1E1B4B)
private val Indigo950 = Color(0xFF312E81)
private val Cyan600 = Color(0xFF0891B2)
private val Cyan200 = Color(0xFF67E8F9)
private val Cyan900 = Color(0xFF164E63)
private val Orange600 = Color(0xFFEA580C)
private val Orange200 = Color(0xFFFDBA74)
private val Orange900 = Color(0xFF7C2D12)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Indigo900,
    secondary = Cyan600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Cyan900,
    tertiary = Orange600,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Orange900,
    background = Color(0xFFFAFAFF),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFFAFAFF),
    onSurface = Color(0xFF191B23),
    surfaceVariant = Color(0xFFE6E7F0),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo200,
    onPrimary = Indigo900,
    primaryContainer = Indigo950,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Cyan200,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF155E6B),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Orange200,
    onTertiary = Color(0xFF4A1D00),
    tertiaryContainer = Orange900,
    onTertiaryContainer = Color(0xFFFFEDD5),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE4E5EE),
    surface = Color(0xFF14161F),
    onSurface = Color(0xFFE4E5EE),
    surfaceVariant = Color(0xFF2A2D3A),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun VortexDLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
