package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myfit.R

// V5.0 新增：组别详情
// V5.3 更新：增加右侧数据支持
data class WorkoutSet(
    val setNumber: Int,
    val weightOrDuration: String, // 默认为左边重量，或双边总重/时长
    val reps: String,             // 默认为左边次数
    val rightWeight: String? = null, // [新增] 右边重量
    val rightReps: String? = null    // [新增] 右边次数
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val id: Int = 0,
    val themeId: Int = 1, // 1) 修改默认值为 1 (GREEN)
    val languageCode: String = "zh",
    // 3) 新增字段：年龄、身高(cm)、性别(0=男, 1=女)
    // 给定默认值以兼容旧数据，虽然数据库迁移会处理，但对象实例化需要默认值
    val age: Int = 0,
    val height: Float = 0f,
    val gender: Int = 0
)

// V5.0 更新：动作模板增加部位和器械
// V5.3 更新：增加单边标记
@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTarget: String, // 保留作为参考目标
    val category: String, // "STRENGTH", "CARDIO", "CORE"
    val bodyPart: String = "", // V5.0 新增: 训练部位 (存储资源Key或自定义文本)
    val equipment: String = "", // V5.0 新增: 器械 (存储资源Key或自定义文本)
    val isDeleted: Boolean = false,
    val isUnilateral: Boolean = false, // [新增] 是否为单边动作
    // [新增] 1. 记录类型
    val logType: Int = 0,
    // [新增] 2. 动作说明
    val instruction: String = "",
    val imageUri: String? = null // [新增] 图片路径
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
    val category: String,
    val bodyPart: String = "",
    val equipment: String = "",

    // ⚠️ 注意：UI中如果需要修改这些值，必须设为 var
    var sets: List<WorkoutSet> = emptyList(),
    var isCompleted: Boolean = false, // 必须是 var 才能被 toggle
    var target: String = "",          // 兼容旧 UI
    var actualWeight: String = "",     // 兼容旧 UI
    val isUnilateral: Boolean = false, // [新增] 继承自 Template，方便 UI 判断
    // [新增] 记录类型 (必须同步到 Task，因为 Template 可能会变，但历史记录不能变)
    val logType: Int = 0,
    val imageUri: String? = null // [新增] 继承自模板的图片
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
    val category: String,
    // V5.2 新增字段，提供默认值以兼容旧代码构造
    val bodyPart: String = "",
    val equipment: String = "",
    val isUnilateral: Boolean = false, // [新增]
    val logType: Int = 0,
    val imageUri: String? = null // [新增]
)

// [新增] 记录类型枚举
enum class LogType(val value: Int) {
    WEIGHT_REPS(0), // 默认：负重 x 次数
    DURATION(1),    // 计时
    REPS_ONLY(2);   // 仅次数

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: WEIGHT_REPS
    }
}

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
        fun fromId(id: Int): AppTheme = values().find { it.id == id } ?: GREEN
    }
}