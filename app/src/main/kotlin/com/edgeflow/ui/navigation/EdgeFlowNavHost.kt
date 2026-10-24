package com.edgeflow.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.ModuleStatusProbe
import com.edgeflow.ui.AboutScreen
import com.edgeflow.ui.ActionPickerScreen
import com.edgeflow.ui.GesturesScreen
import com.edgeflow.ui.FloatingBarScreen
import com.edgeflow.ui.HomeScreen
import com.edgeflow.ui.KeysScreen
import com.edgeflow.ui.PanelsScreen
import com.edgeflow.ui.SavedActionsScreen
import com.edgeflow.ui.SettingsScreen
import com.edgeflow.ui.StateTriggersScreen

/**
 * Navigation graph for the app.
 *
 * Detail screens are pushed on top of the tab graph rather than being tabs
 * themselves, so the bottom bar stays anchored to the five top-level areas. The
 * bar hides itself on detail screens, which is what makes the transition to a
 * full-screen editor read as a step deeper rather than a lateral move.
 */
@Composable
fun EdgeFlowNavHost(
    configRepository: ConfigRepository,
    statusProbe: ModuleStatusProbe,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Destination.tabs.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expandedLayout = maxWidth >= 700.dp
        Scaffold(
            bottomBar = {
                if (showBottomBar && !expandedLayout) {
                    EdgeFlowBottomBar(navController = navController)
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showBottomBar && expandedLayout) {
                    EdgeFlowNavigationRail(navController = navController)
                }
                NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
                    modifier = Modifier.weight(1f),

            // Detail screens slide in from the end, tabs cross-fade. Matching the
            // motion to the relationship between screens is what makes the
            // hierarchy legible without any other cue.
            enterTransition = { slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(TRANSITION_MILLIS)) + fadeIn(tween(TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS / 2)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 6 },
                    animationSpec = tween(TRANSITION_MILLIS),
                ) + fadeOut(tween(TRANSITION_MILLIS))
            },
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    configRepository = configRepository,
                    statusProbe = statusProbe,
                    onOpenGestures = { navController.navigate(Destination.Gestures.route) },
                    onOpenKeys = { navController.navigate(Destination.Keys.route) },
                    onOpenStateTriggers = { navController.navigate(Destination.StateTriggers.route) },
                    onOpenAbout = { navController.navigate(Destination.About.route) },
                )
            }

            composable(Destination.Gestures.route) {
                GesturesScreen(
                    configRepository = configRepository,
                    onPickAction = { family, primary, secondary ->
                        navController.navigate(Destination.ActionPicker.create(family, primary, secondary))
                    },
                )
            }

            composable(Destination.Keys.route) {
                KeysScreen(
                    configRepository = configRepository,
                    onPickAction = { family, primary, secondary ->
                        navController.navigate(Destination.ActionPicker.create(family, primary, secondary))
                    },
                )
            }

            composable(Destination.Panels.route) {
                PanelsScreen(
                    configRepository = configRepository,
                    onPickAction = { family, primary, secondary ->
                        navController.navigate(Destination.ActionPicker.create(family, primary, secondary))
                    },
                )
            }

            composable(Destination.Settings.route) {
                SettingsScreen(configRepository = configRepository)
            }

            composable(Destination.FloatingBar.route) {
                FloatingBarScreen(repository = configRepository)
            }

            composable(Destination.StateTriggers.route) {
                StateTriggersScreen(
                    configRepository = configRepository,
                    onPickAction = { family, primary, secondary, target ->
                        navController.navigate(Destination.ActionPicker.create(family, primary, secondary, target))
                    },
                )
            }

            composable(Destination.SavedActions.route) {
                SavedActionsScreen(
                    configRepository = configRepository,
                    onPickAction = { family, primary, secondary ->
                        navController.navigate(Destination.ActionPicker.create(family, primary, secondary))
                    },
                )
            }

            composable(Destination.About.route) {
                AboutScreen(statusProbe = statusProbe)
            }

            composable(
                route = Destination.ActionPicker.route,
                arguments = listOf(
                    navArgument(Destination.ActionPicker.ARG_FAMILY) { type = NavType.IntType },
                    navArgument(Destination.ActionPicker.ARG_PRIMARY) { type = NavType.IntType },
                    navArgument(Destination.ActionPicker.ARG_SECONDARY) { type = NavType.IntType },
                    navArgument(Destination.ActionPicker.ARG_TARGET) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val family = entry.arguments?.getInt(Destination.ActionPicker.ARG_FAMILY) ?: 0
                val primary = entry.arguments?.getInt(Destination.ActionPicker.ARG_PRIMARY) ?: 0
                val secondary = entry.arguments?.getInt(Destination.ActionPicker.ARG_SECONDARY) ?: 0
                ActionPickerScreen(
                    configRepository = configRepository,
                    family = BindingFamily.of(family) ?: BindingFamily.EDGE_GESTURE,
                    primary = primary,
                    secondary = secondary,
                    target = entry.arguments?.getString(Destination.ActionPicker.ARG_TARGET),
                    onDone = { navController.popBackStack() },
                )
            }
                }
            }
        }
    }
}

/** Bottom navigation bar, driven by [Destination.tabs]. */
@Composable
private fun EdgeFlowBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        Destination.tabs.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
                    navController.navigate(destination.route) {
                        // Tabs replace each other rather than stacking, so the
                        // back gesture always returns to the app the user came
                        // from instead of walking back through tab history.
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    destination.icon?.let { Icon(it, contentDescription = null) }
                },
                label = { destination.label?.let { Text(it) } },
            )
        }
    }
}

@Composable
private fun EdgeFlowNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationRail {
        Destination.tabs.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationRailItem
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { destination.icon?.let { Icon(it, contentDescription = destination.label) } },
                label = { destination.label?.let { Text(it) } },
                alwaysShowLabel = false,
            )
        }
    }
}

private const val TRANSITION_MILLIS = 220
