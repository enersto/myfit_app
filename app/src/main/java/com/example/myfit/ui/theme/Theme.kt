package com.example.myfit.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.myfit.model.AppTheme

@Composable
fun MyFitTheme(
    appTheme: AppTheme, // 必须与 MainActivity 调用一致
    content: @Composable () -> Unit
) {
    // 根据 AppTheme 枚举生成颜色方案
    val primaryColor = Color(appTheme.primary)
    val background = Color(appTheme.background)
    val onBackground = Color(appTheme.onBackground)

    // 简单起见，这里直接构造 ColorScheme
    // 如果是 DARK 主题(ID=0)，强制深色；其他主题强制亮色底，但用对应的主色调
    val colorScheme = if (appTheme.id == 0) {
        darkColorScheme(
            primary = primaryColor,
            background = background,
            surface = Color(0xFF1E1E1E), // 深灰
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            background = background,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = onBackground,
            onSurface = onBackground
        )
    }

    // 设置状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = primaryColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (appTheme.id != 0)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}