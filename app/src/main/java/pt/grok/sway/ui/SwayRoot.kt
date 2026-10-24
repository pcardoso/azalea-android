package pt.grok.sway.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.SwayApplication
import pt.grok.sway.data.DiaryEntry
import pt.grok.sway.ui.diary.DiaryEntryDetailScreen
import pt.grok.sway.ui.diary.DiaryEntryFormScreen
import pt.grok.sway.ui.diary.DiaryScreen
import pt.grok.sway.ui.exercises.ExerciseSessionScreen
import pt.grok.sway.ui.exercises.ExercisesScreen
import pt.grok.sway.ui.settings.AccountScreen
import pt.grok.sway.ui.settings.CreditsScreen
import pt.grok.sway.ui.settings.SettingsScreen
import pt.grok.sway.ui.settings.SignInScreen
import pt.grok.sway.ui.today.TodayScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Today("today", R.string.tab_today, Icons.Outlined.WbSunny),
    Diary("diary", R.string.tab_diary, Icons.AutoMirrored.Outlined.MenuBook),
    Exercises("exercises", R.string.tab_exercises, Icons.Outlined.SelfImprovement),
    Settings("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

/** Navigation for the whole app: four tabs, plus full-screen forms and the exercise session. */
object Routes {
    const val SIGN_IN = "signin"
    const val ACCOUNT = "account"
    const val CREDITS = "credits"
    const val SESSION = "session"
    fun newEntry(kind: DiaryEntry.Kind) = "entry/new/${kind.name}"
    fun entry(id: String) = "entry/$id"
}

@Composable
fun SwayRoot(app: SwayApplication) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = Tab.entries.any { it.route == currentRoute }

    LaunchedEffect(Unit) {
        runCatching { app.session.refreshPrescription() }
    }
    // Retry anything still waiting to reach the server whenever the app comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            val token = app.session.state.value.submissionToken
            app.diary.syncPending(token)
            app.exerciseRuns.syncPending(token)
        }
    }

    CompositionLocalProvider(LocalApp provides app) {
        Scaffold(
            bottomBar = {
                if (showTabs) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier) {
                composable(Tab.Today.route) { TodayScreen(nav, padding) }
                composable(Tab.Diary.route) { DiaryScreen(nav, padding) }
                composable(Tab.Exercises.route) { ExercisesScreen(nav, padding) }
                composable(Tab.Settings.route) { SettingsScreen(nav, padding) }
                composable("entry/new/{kind}") { entry ->
                    val kind = DiaryEntry.Kind.valueOf(entry.arguments?.getString("kind") ?: "Drink")
                    DiaryEntryFormScreen(nav, kind)
                }
                composable("entry/{id}") { entry ->
                    DiaryEntryDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
                }
                composable(Routes.SIGN_IN) { SignInScreen(nav) }
                composable(Routes.ACCOUNT) { AccountScreen(nav) }
                composable(Routes.CREDITS) { CreditsScreen(nav) }
                composable(Routes.SESSION) { ExerciseSessionScreen(nav) }
            }
        }
    }
}
