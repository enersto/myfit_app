package com.example.myfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfit.ui.MainScreen
import com.example.myfit.ui.theme.MyFitTheme
import com.example.myfit.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()

            // 修正：直接把 currentTheme (AppTheme类型) 传给 Theme
            MyFitTheme(appTheme = currentTheme) {
                MainScreen()
            }
        }
    }
}