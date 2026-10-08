package com.inc.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A near-monochrome "ink on paper" palette.
 *
 * Design intent: one dark ink tone, one warm paper tone, no loud brand color.
 * Accent color is reserved for the sync status states so that color in the UI
 * always *means* something (data safety), which keeps the app quiet.
 */

// Core ink + paper
val Ink = Color(0xFF1B1B1F)
val Paper = Color(0xFFFAFAF8)

val InkNight = Color(0xFFE7E7E2)
val PaperNight = Color(0xFF101012)

// Sync state accents (used by SyncStatusChip and history rows)
val SyncSuccessLight = Color(0xFF1E7A4B)
val SyncSuccessDark = Color(0xFF5DD69A)
val SyncWarningLight = Color(0xFF9A6700)
val SyncWarningDark = Color(0xFFE5B454)
val SyncErrorLight = Color(0xFFBA1A1A)
val SyncErrorDark = Color(0xFFFFB4AB)
val SyncInfoLight = Color(0xFF3E5BFF)
val SyncInfoDark = Color(0xFFA8B8FF)

val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E7E2),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF55555A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDE8),
    onSecondaryContainer = Color(0xFF232327),
    tertiary = SyncInfoLight,
    onTertiary = Color(0xFFFFFFFF),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF55555A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8F5),
    surfaceContainer = Color(0xFFF3F3EF),
    surfaceContainerHigh = Color(0xFFEDEDE9),
    surfaceContainerHighest = Color(0xFFE7E7E2),
    outline = Color(0xFFC7C7C1),
    outlineVariant = Color(0xFFE3E3DE),
    error = SyncErrorLight,
    onError = Color(0xFFFFFFFF),
)

val DarkColorScheme = darkColorScheme(
    primary = InkNight,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A2A2F),
    onPrimaryContainer = InkNight,
    secondary = Color(0xFFC4C4BF),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A3A3F),
    onSecondaryContainer = Color(0xFFE7E7E2),
    tertiary = SyncInfoDark,
    onTertiary = Ink,
    background = PaperNight,
    onBackground = InkNight,
    surface = Color(0xFF17171A),
    onSurface = InkNight,
    surfaceVariant = Color(0xFF1E1E22),
    onSurfaceVariant = Color(0xFFA8A8A3),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF1E1E22),
    surfaceContainerHighest = Color(0xFF2A2A2F),
    outline = Color(0xFF4A4A50),
    outlineVariant = Color(0xFF2C2C31),
    error = SyncErrorDark,
    onError = Ink,
)
