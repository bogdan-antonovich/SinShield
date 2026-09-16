package com.example.sinshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF087CF0),
    secondary = Color(0xFF00D782),
    background = Color(0xFFE5EFFF),
    surface = Color(0xFFF9FBFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF082D48),
    onSurface = Color(0xFF082D48)
)

@Composable
fun SinSheldTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
