package com.example.myfit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("home", "history", "settings")) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_home)) },
                        selected = currentRoute == "home",
                        onClick = { navController.navigate("home") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_history)) },
                        selected = currentRoute == "history",
                        onClick = { navController.navigate("history") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        selected = currentRoute == "settings",
                        onClick = { navController.navigate("settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                DailyPlanScreen(viewModel, navController)
            }
            composable("history") {
                HistoryScreen(viewModel)
            }
            composable("settings") {
                // [修复] 传递 navController
                ScheduleScreen(navController, viewModel)
            }
            composable("exercise_manager") {
                ExerciseManagerScreen(viewModel, navController)
            }
        }
    }
}