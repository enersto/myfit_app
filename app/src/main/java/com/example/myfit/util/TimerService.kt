package com.example.myfit.util

import android.app.Service
import android.content.Context // [修复] 补全了缺失的 Context 导入
import android.content.Intent
import android.os.IBinder

class TimerService : Service() {

    companion object {
        const val ACTION_START_TIMER = "ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "ACTION_STOP_TIMER"
        const val EXTRA_TASK_NAME = "EXTRA_TASK_NAME"
        const val EXTRA_END_TIME = "EXTRA_END_TIME"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Training"
                val endTime = intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis())

                startForegroundService(taskName, endTime)
            }
            ACTION_STOP_TIMER -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(taskName: String, endTime: Long) {
        // 获取初始 Notification
        val notification = NotificationHelper.buildNotification(this, taskName, endTime)

        // [关键] 直接调用基础的 startForeground
        // 不传 0，不传 ServiceInfo 类型，由系统默认处理，兼容性最强
        try {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
        NotificationHelper.cancelNotification(this)
    }

    // [注意] 这个函数其实主要是给 UI (Activity/Composable) 用的
    // 放在 Service 里虽然现在能编译通过，但一般 Service 不会直接调用它。
    // 你可以保留它在这里作为参考，或者复制到你的 UI 代码中去调用。
    fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
            // 适配不同版本的 Android 参数
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
            // 如果是在 Service 中启动 Activity，通常需要加这个 Flag
            // 但如果是由 Activity 调用的 context，则不需要
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}