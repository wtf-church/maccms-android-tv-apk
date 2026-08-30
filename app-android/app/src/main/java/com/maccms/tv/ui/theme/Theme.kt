package com.maccms.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors

private val MaccmsColorScheme = darkColors(
    primary = NetflixRed,
    background = NetflixBlack,
    surface = Gray10,
    onPrimary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun MaccmsTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaccmsColorScheme,
        content = content
    )
}
