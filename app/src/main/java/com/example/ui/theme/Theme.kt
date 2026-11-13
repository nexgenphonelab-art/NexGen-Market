package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = NexGenCoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4B160E),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = NexGenCyanGlow,
    onSecondary = Color(0xFF001F33),
    secondaryContainer = Color(0xFF0C2D4D),
    onSecondaryContainer = Color(0xFFD0E8FF),
    tertiary = NexGenEmerald,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = NexGenRose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = NexGenCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DF),
    onPrimaryContainer = Color(0xFF5C1104),
    secondary = NexGenCobalt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEEFF),
    onSecondaryContainer = Color(0xFF002652),
    tertiary = NexGenEmerald,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = NexGenRose,
    onError = Color.White
)

val NexGenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexGenMarketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NexGenShapes,
        content = content
    )
}
