package com.inc.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Route definitions in one place.
 *
 * String routes keep the navigation graph readable; argument names are
 * constants so the editor screen and the graph can't drift apart.
 */
object Routes {
    const val NOTES = "notes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val FOLDERS = "folders"
    const val SYNC_STATUS = "sync_status"
    const val AUTH = "auth"

    const val NOTE_ARG = "noteId"
    const val EDITOR = "note/{$NOTE_ARG}"
    fun editor(noteId: String) = "note/$noteId"

    /** Sentinel for creating a new note; the row is written on first input. */
    const val NEW_NOTE = "new"
}

/** Destinations that show the bottom navigation bar / rail. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    NOTES(Routes.NOTES, "Notes", Icons.AutoMirrored.Filled.Notes),
    SEARCH(Routes.SEARCH, "Search", Icons.Filled.Search),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
