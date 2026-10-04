package com.knowapp.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Maps 1:1 onto frontend/css/styles.css's :root / [data-mode="dark"] tokens:
// surface -> surface, surface-2 -> surfaceVariant, ink -> onSurface,
// ink-soft -> onSurfaceVariant, line -> outline, bg -> background.
private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = BrandInk,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    background = BgLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = InkSoftLight,
    outline = LineLight,
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = BrandInk,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    background = BgDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = InkSoftDark,
    outline = LineDark,
    error = DangerRed,
)

@Composable
fun KnowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic (wallpaper-derived) color is deliberately not offered here —
    // the whole point of this pass is to keep the app's teal brand
    // consistent with the web app rather than per-device.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        activity?.window?.let { window ->
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KnowTypography,
        shapes = KnowShapes,
        content = content,
    )
}
