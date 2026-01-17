package com.example.myfit

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.myfit.data.AppDatabase
import com.example.myfit.ui.MainScreen
import com.example.myfit.ui.theme.MyFitTheme
import com.example.myfit.util.LocaleHelper
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : ComponentActivity() {
    // 使用 viewModels() 委托获取 ViewModel 实例
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // 监听 ViewModel 中的主题和语言设置
            val currentTheme by viewModel.currentTheme.collectAsState()
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val context = LocalContext.current

            // 🌟 核心修复逻辑 1：监听语言变化并重启 Activity 🌟
            LaunchedEffect(currentLanguage) {
                // 1. 获取当前界面实际显示的语言
                val config = context.resources.configuration
                val sysLocale = config.locales[0]
                val currentDisplayLanguage = sysLocale.language

                // 2. 只有当“想要的语言”和“正在显示的语言”不一样时，才重启
                // currentLanguage.isNotEmpty() 防止初始空值触发重启
                if (currentDisplayLanguage != currentLanguage && currentLanguage.isNotEmpty()) {
                    // 应用新语言配置
                    LocaleHelper.setLocale(context, currentLanguage)
                    // 重启 Activity 以重新加载 strings.xml 资源
                    (context as? Activity)?.recreate()
                }
            }

            // 应用主题
            MyFitTheme(appTheme = currentTheme) {
                // [修复] 传递 viewModel 给 MainScreen
                MainScreen(viewModel = viewModel)
            }
        }
    }

    // 🌟 核心修复逻辑 2：在 Activity 创建前注入语言环境 🌟
    // 如果没有这个方法，重启 Activity 后语言会变回系统默认
    override fun attachBaseContext(newBase: Context) {
        // 使用 runBlocking 从数据库同步读取语言设置
        // 注意：这里必须是同步读取，因为 super.attachBaseContext 必须立即拿到 Context
        val languageCode = try {
            runBlocking {
                val db = AppDatabase.getDatabase(newBase)
                // 获取第一条设置记录
                val setting = db.workoutDao().getAppSettings().first()
                setting?.languageCode ?: "zh" // 默认为中文
            }
        } catch (e: Exception) {
            "zh"
        }
        // 设置 Context 的语言环境
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }
}