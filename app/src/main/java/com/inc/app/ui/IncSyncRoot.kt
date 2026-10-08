package com.inc.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inc.app.ui.navigation.IncSyncNavHost
import com.inc.app.ui.navigation.Routes
import com.inc.app.ui.navigation.TopLevelDestination

/**
 * Adaptive app shell.
 *
 * One navigation graph and one ViewModel layer; only the *chrome* changes with
 * window size:
 *  - compact (phone portrait): bottom NavigationBar
 *  - medium/expanded (tablet, split screen, landscape): NavigationRail
 *
 * This is the adaptive-layout strategy for the whole project: business logic
 * never forks per screen size, only layout does. The rail replaces the bottom
 * bar when there is enough horizontal room, because on a wide screen the
 * bottom bar steals vertical space that isn't scarce.
 */
@Composable
fun IncSyncRoot(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val onTopLevel = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }
    // The editor is a full-screen task: no persistent navigation chrome,
    // maximum room above the keyboard.
    val showChrome = onTopLevel

    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        bottomBar = {
            if (showChrome && !useRail) {
                IncSyncNavigationBar(
                    currentDestination = currentDestination,
                    onNavigate = { navController.navigateTopLevel(it) },
                )
            }
        },
    ) { innerPadding ->
        if (showChrome && useRail) {
            Row(Modifier.padding(innerPadding)) {
                IncSyncNavigationRail(
                    currentDestination = currentDestination,
                    onNavigate = { navController.navigateTopLevel(it) },
                )
                Box(Modifier.weight(1f)) {
                    IncSyncNavHost(navController = navController)
                }
            }
        } else {
            Box(Modifier.padding(innerPadding)) {
                IncSyncNavHost(navController = navController)
            }
        }
    }
}

@Composable
private fun IncSyncNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
            )
        }
    }
}

@Composable
private fun IncSyncNavigationRail(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationRail(header = { Box(Modifier.padding(top = 16.dp)) }) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
            )
        }
    }
}

/**
 * Standard resilient top-level navigation: selecting a tab resets to that
 * graph's start destination and keeps each tab's own back stack, so Back
 * behaves the way users expect.
 */
private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
