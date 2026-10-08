package com.inc.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inc.app.ui.folders.FoldersScreen
import com.inc.app.ui.notes.NoteEditorScreen
import com.inc.app.ui.notes.NotesListScreen
import com.inc.app.ui.search.SearchScreen
import com.inc.app.ui.settings.SettingsScreen
import com.inc.app.ui.sync.SyncStatusScreen

/**
 * The app's single navigation graph.
 *
 * The NavHost is the only place that knows which composable serves which
 * route; screens are wired to ViewModels internally via `hiltViewModel()`, so
 * navigation never passes data by hand — it passes a route with an id, and the
 * editor's ViewModel reads the id from [androidx.lifecycle.SavedStateHandle].
 * That also means the editor survives process death: the id is in the back
 * stack entry, not in memory.
 */
@Composable
fun IncSyncNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.NOTES,
        modifier = modifier,
        // Minimal, purposeful motion: a short cross-fade, no slide theatrics.
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Routes.NOTES) {
            NotesListScreen(
                onOpenNote = { navController.navigate(Routes.editor(it)) },
                onNewNote = { navController.navigate(Routes.editor(Routes.NEW_NOTE)) },
                onOpenFolders = { navController.navigate(Routes.FOLDERS) },
                onOpenSyncStatus = { navController.navigate(Routes.SYNC_STATUS) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onOpenNote = { navController.navigate(Routes.editor(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenSyncStatus = { navController.navigate(Routes.SYNC_STATUS) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.NOTE_ARG) { type = NavType.StringType }),
        ) {
            NoteEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FOLDERS) {
            FoldersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SYNC_STATUS) {
            SyncStatusScreen(onBack = { navController.popBackStack() })
        }
    }
}
