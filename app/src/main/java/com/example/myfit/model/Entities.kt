package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myfit.R

// V5.0 新增：组别详情
data class WorkoutSet(
    val setNumber: Int,
    val weightOrDuration: String, // 重量 或 时长
    val reps: String              // 次数
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val id: Int = 0,
    val themeId: Int = 0,
    val languageCode: String = "zh"
)

// V5.0 更新：动作模板增加部位和器械
@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTarget: String, // 保留作为参考目标
    val category: String, // "STRENGTH", "CARDIO", "CORE"
    val bodyPart: String = "", // V5.0 新增: 训练部位 (存储资源Key或自定义文本)
    val equipment: String = "", // V5.0 新增: 器械 (存储资源Key或自定义文本)
    val isDeleted: Boolean = false
)

@Entity(tableName = "schedule_config")
data class ScheduleConfig(
    @PrimaryKey val dayOfWeek: Int,
    val dayType: DayType
)

// V5.0 更新：任务增加 List<WorkoutSet>
@Entity(tableName = "workout_tasks")
data class WorkoutTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long,
    val name: String,
    val category: String, // "STRENGTH", "CARDIO", "CORE"

    // V5.0 新增详细数据
    val bodyPart: String = "",
    val equipment: String = "",
    val sets: List<WorkoutSet> = emptyList(), // 核心变化：存储多组数据

    // 兼容旧版本 UI 的字段 (后续 UI 更新中将逐步弱化)
    var target: String = "",
    var actualWeight: String = "",

    var isCompleted: Boolean = false
)

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weight: Float
)

@Entity(tableName = "weekly_routine")
data class WeeklyRoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val templateId: Long,
    val name: String,
    val target: String,
    val category: String
)

enum class DayType(val labelResId: Int, val colorHex: Long) {
    CORE(R.string.type_core, 0xFFFF5722),
    ACTIVE_REST(R.string.type_active, 0xFF4CAF50),
    LIGHT(R.string.type_light, 0xFF03A9F4),
    REST(R.string.type_rest, 0xFF9E9E9E)
}

enum class AppTheme(val id: Int, val primary: Long, val background: Long, val onBackground: Long) {
    DARK(0, 0xFFFF5722, 0xFF121212, 0xFFFFFFFF),
    GREEN(1, 0xFF4CAF50, 0xFFF1F8E9, 0xFF1B5E20),
    BLUE(2, 0xFF2196F3, 0xFFE3F2FD, 0xFF0D47A1),
    YELLOW(3, 0xFFFFC107, 0xFFFFFDE7, 0xFFBF360C),
    GREY(4, 0xFF607D8B, 0xFFECEFF1, 0xFF263238);

    companion object {
        fun fromId(id: Int): AppTheme = values().find { it.id == id } ?: DARK
    }
}