package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ... (AppSetting, ExerciseTemplate, ScheduleConfig 保持不变) ...
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val id: Int = 0,
    val themeId: Int = 0
)

@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTarget: String,
    val category: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "schedule_config")
data class ScheduleConfig(
    @PrimaryKey val dayOfWeek: Int,
    val dayType: DayType
)

// ▼▼▼ V4.0 新增：周度方案明细表 ▼▼▼
@Entity(tableName = "weekly_routine")
data class WeeklyRoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1=周一, 7=周日
    val templateId: Long, // 关联动作库ID
    val name: String,     // 冗余存储，方便导出
    val target: String,
    val category: String
)
// ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

// ... (WorkoutTask, WeightRecord, DayType, AppTheme 保持不变) ...
@Entity(tableName = "workout_tasks")
data class WorkoutTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long,
    val name: String,
    var target: String,
    var actualWeight: String = "",
    var isCompleted: Boolean = false,
    val type: String
)

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weight: Float
)

enum class DayType(val label: String, val colorHex: Long) {
    CORE("核心训练日", 0xFFFF5722),
    ACTIVE_REST("动态恢复日", 0xFF4CAF50),
    LIGHT("轻松活动日", 0xFF03A9F4),
    REST("休息日", 0xFF9E9E9E)
}

enum class AppTheme(val id: Int, val label: String, val primary: Long, val background: Long, val onBackground: Long) {
    DARK(0, "硬核深色", 0xFFFF5722, 0xFF121212, 0xFFFFFFFF),
    GREEN(1, "清新浅绿", 0xFF4CAF50, 0xFFF1F8E9, 0xFF1B5E20),
    BLUE(2, "宁静浅蓝", 0xFF2196F3, 0xFFE3F2FD, 0xFF0D47A1),
    YELLOW(3, "活力浅黄", 0xFFFFC107, 0xFFFFFDE7, 0xFFBF360C),
    GREY(4, "极简商务", 0xFF607D8B, 0xFFECEFF1, 0xFF263238);

    companion object {
        fun fromId(id: Int): AppTheme = values().find { it.id == id } ?: DARK
    }
}