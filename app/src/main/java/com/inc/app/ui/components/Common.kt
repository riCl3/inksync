package com.inc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inc.app.domain.model.SyncStatus
import com.inc.app.ui.theme.SyncErrorDark
import com.inc.app.ui.theme.SyncErrorLight
import com.inc.app.ui.theme.SyncInfoDark
import com.inc.app.ui.theme.SyncInfoLight
import com.inc.app.ui.theme.SyncSuccessDark
import com.inc.app.ui.theme.SyncSuccessLight
import com.inc.app.ui.theme.SyncWarningDark
import com.inc.app.ui.theme.SyncWarningLight
import androidx.compose.ui.graphics.luminance

/**
 * Maps a [SyncStatus] to its presentation: a dot color and a short label.
 *
 * Sync states are the only place in the app that uses saturated color, so
 * users learn to read color as "data safety" instantly.
 */
@Composable
fun syncPresentation(status: SyncStatus): Pair<Color, String> {
    // Derive "dark" from the active color scheme rather than the system
    // setting, so the dot stays correct when the user forces light/dark
    // theme on the Settings screen.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = when (status) {
        SyncStatus.SYNCED -> if (dark) SyncSuccessDark else SyncSuccessLight
        SyncStatus.SYNCING -> if (dark) SyncInfoDark else SyncInfoLight
        SyncStatus.OFFLINE -> if (dark) SyncWarningDark else SyncWarningLight
        SyncStatus.PENDING -> if (dark) SyncWarningDark else SyncWarningLight
        SyncStatus.FAILED -> if (dark) SyncErrorDark else SyncErrorLight
        SyncStatus.CONFLICT -> if (dark) SyncErrorDark else SyncErrorLight
    }
    val label = when (status) {
        SyncStatus.SYNCED -> "Synced"
        SyncStatus.SYNCING -> "Syncing…"
        SyncStatus.OFFLINE -> "Offline"
        SyncStatus.PENDING -> "Pending"
        SyncStatus.FAILED -> "Sync failed"
        SyncStatus.CONFLICT -> "Conflict"
    }
    return color to label
}

/**
 * Compact status pill for the top bar: a colored dot + label.
 *
 * Deliberately quiet (no borders, no background) — the user should be able to
 * glance at it, not read it.
 */
@Composable
fun SyncStatusChip(
    status: SyncStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, label) = syncPresentation(status)
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Centered empty state used by every list screen. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
