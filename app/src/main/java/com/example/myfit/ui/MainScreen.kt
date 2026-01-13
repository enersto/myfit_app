package com.example.myfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
// [新增] 震动反馈相关 Import
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel

import androidx.compose.ui.platform.LocalView // [新增]
import android.view.HapticFeedbackConstants // [新增]

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object DailyPlan : Screen("daily_plan", R.string.tab_home, Icons.Default.CalendarToday)
    object History : Screen("history", R.string.tab_history, Icons.Default.History)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val screens = listOf(Screen.DailyPlan, Screen.History, Screen.Settings)

// [修改] 不再使用 Compose 的 LocalHapticFeedback，改用原生 View
    val view = LocalView.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // [修复] 使用原生 View 的 VIRTUAL_KEY 震动
                            // 这种震动是短促的“哒”声，系统不会轻易屏蔽，且符合按钮点击的触感
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.DailyPlan.route
            ) {
                composable(Screen.DailyPlan.route) {
                    // 保持之前修复的参数顺序
                    DailyPlanScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel)
                }
                composable(Screen.Settings.route) {
                    ScheduleScreen(navController, viewModel)
                }
                composable("exercise_manager") {
                    ExerciseManagerScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}