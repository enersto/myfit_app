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

            // [修改 1] 将状态栏设为透明
            // 解决部分机型显示灰色遮罩的问题，同时消除 'statusBarColor' 相关的视觉不一致
            // 注意：这需要您的 Scaffold TopBar 正确延伸到顶部（Material3 默认支持）
            window.statusBarColor = Color.Transparent.toArgb()

            // [修改 2] 精确控制状态栏图标颜色 (黑/白)
            val insetsController = WindowCompat.getInsetsController(window, view)

            // 逻辑说明：
            // 如果是深色主题(id=0)，图标必须是白色 -> isAppearanceLightStatusBars = false
            // 如果是浅色主题(id!=0)，通常背景是浅色，图标应为黑色 -> isAppearanceLightStatusBars = true
            // *特殊情况*：如果您的 TopBar 是深色（例如用了深绿色的 Primary），那么即使是浅色主题，图标也应该设为白色(false)。
            // 假设浅色主题下 TopBar 跟随 Primary Color (深色)，则建议全程使用白色图标：
            // insetsController.isAppearanceLightStatusBars = false

            // 按照您之前的逻辑（浅色主题用黑字）：
            insetsController.isAppearanceLightStatusBars = (appTheme.id != 0)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}