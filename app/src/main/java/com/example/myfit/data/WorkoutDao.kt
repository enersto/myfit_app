package com.example.myfit.data

import androidx.room.*
import com.example.myfit.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- Tasks (原有) ---
    @Query("SELECT * FROM workout_tasks WHERE date = :date")
    fun getTasksForDate(date: String): Flow<List<WorkoutTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkoutTask)

    @Update
    suspend fun updateTask(task: WorkoutTask)

    @Delete
    suspend fun deleteTask(task: WorkoutTask)

    // --- Templates (V5.3 增强) ---
    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0")
    fun getAllTemplates(): Flow<List<ExerciseTemplate>>

    // [修改] 必须返回 Long (新插入行的 ID)，用于导入时关联
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplate): Long

    @Update
    suspend fun updateTemplate(template: ExerciseTemplate)

    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteTemplate(id: Long)

    @Query("SELECT COUNT(*) FROM exercise_templates")
    suspend fun getTemplateCount(): Int

    @Query("SELECT * FROM exercise_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): ExerciseTemplate?

    // [V5.3 新增] 用于导入时检查动作是否存在 (按名称查找)
    @Query("SELECT * FROM exercise_templates WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getTemplateByName(name: String): ExerciseTemplate?

    // [V5.3 新增] 用于去重逻辑：获取所有未删除的模板 (同步方法)
    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0")
    suspend fun getAllTemplatesSync(): List<ExerciseTemplate>

    // [V5.3 新增] 批量软删除 (用于去重)
    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun softDeleteTemplates(ids: List<Long>)

    // --- Schedule Config (原有) ---
    @Query("SELECT * FROM schedule_config")
    fun getAllSchedules(): Flow<List<ScheduleConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(config: ScheduleConfig)

    @Query("SELECT COUNT(*) FROM schedule_config")
    suspend fun getScheduleCount(): Int

    // --- Weekly Routine (原有) ---
    @Query("SELECT * FROM weekly_routine WHERE dayOfWeek = :day")
    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineItem(item: WeeklyRoutineItem)

    @Delete
    suspend fun deleteRoutineItem(item: WeeklyRoutineItem)

    @Query("DELETE FROM weekly_routine")
    suspend fun clearWeeklyRoutine()

    // --- Weight (原有) ---
    @Query("SELECT * FROM weight_records ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY date DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: WeightRecord)

    // --- History (原有) ---
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    fun getHistoryRecords(): Flow<List<WorkoutTask>>

    // [V5.2 新增] 用于 CSV 导出
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    suspend fun getHistoryRecordsSync(): List<WorkoutTask>

    // --- App Settings (原有) ---
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getAppSettings(): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(setting: AppSetting)
}