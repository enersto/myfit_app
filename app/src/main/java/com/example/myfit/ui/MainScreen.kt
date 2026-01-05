package com.example.myfit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState // 关键引用
import androidx.navigation.compose.rememberNavController
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val currentTheme by viewModel.currentTheme.collectAsState()

    // V4.0 Bug Fix: 显式监听路由堆栈变化
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val items = listOf(
                    Triple("打卡", Icons.Default.DateRange, "home"),
                    Triple("历史", Icons.Default.History, "history"),
                    Triple("设置", Icons.Default.EditCalendar, "schedule")
                )
                items.forEach { (label, icon, route) ->
                    // 修复：使用实时监听的 currentRoute 进行对比
                    val isSelected = currentRoute == route
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
            composable("home") { DailyPlanScreen(viewModel, navController) }
            composable("history") { HistoryScreen(viewModel) }
            composable("schedule") { ScheduleScreen(navController, viewModel) }
            composable("exercise_manager") { ExerciseManagerScreen(navController, viewModel) }
        }
    }
}