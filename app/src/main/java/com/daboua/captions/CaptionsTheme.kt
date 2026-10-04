package com.daboua.captions

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Captions FA
 * Professional dark theme
 */

private val CaptionsColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color(0xFF17131F),

    primaryContainer = Color(0xFF302951),
    onPrimaryContainer = Color(0xFFE8E2FF),

    secondary = Color(0xFFB8A9FF),
    onSecondary = Color(0xFF1B1724),

    background = Color(0xFF090B10),
    onBackground = Color(0xFFF5F3F8),

    surface = Color(0xFF10131A),
    onSurface = Color(0xFFF5F3F8),

    surfaceVariant = Color(0xFF1A1E27),
    onSurfaceVariant = Color(0xFFA9A6B2),

    outline = Color(0xFF3B3E49),
    outlineVariant = Color(0xFF292C35),

    error = Color(0xFFFF7180),
    onError = Color(0xFF2B0710),

    errorContainer = Color(0xFF4A101A),
    onErrorContainer = Color(0xFFFFD9DD)
)

private val CaptionsTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold
    ),

    displayMedium = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    ),

    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    ),

    headlineMedium = TextStyle(
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold
    ),

    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    ),

    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),

    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),

    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    ),

    bodyLarge = TextStyle(
        fontSize = 15.sp
    ),

    bodyMedium = TextStyle(
        fontSize = 13.sp
    ),

    bodySmall = TextStyle(
        fontSize = 12.sp
    ),

    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    ),

    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    ),

    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun CaptionsProfessionalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CaptionsColors,
        typography = CaptionsTypography,
        content = content
    )
}
