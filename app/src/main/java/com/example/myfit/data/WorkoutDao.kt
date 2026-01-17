package com.example.myfit.data

import androidx.room.*
import com.example.myfit.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- Tasks ---
    @Query("SELECT * FROM workout_tasks WHERE date = :date")
    fun getTasksForDate(date: String): Flow<List<WorkoutTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkoutTask)

    @Update
    suspend fun updateTask(task: WorkoutTask)

    @Delete
    suspend fun deleteTask(task: WorkoutTask)

    @Query("SELECT * FROM workout_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): WorkoutTask?

    // --- Templates ---
    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0")
    fun getAllTemplates(): Flow<List<ExerciseTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplate): Long

    // [新增] 批量插入，用于预填充
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<ExerciseTemplate>)

    @Update
    suspend fun updateTemplate(template: ExerciseTemplate)

    @Delete
    suspend fun deleteTemplate(template: ExerciseTemplate)

    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteTemplate(id: Long)

    @Query("SELECT * FROM exercise_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): ExerciseTemplate?

    @Query("SELECT * FROM exercise_templates WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getTemplateByName(name: String): ExerciseTemplate?

    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0")
    suspend fun getAllTemplatesSync(): List<ExerciseTemplate>

    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun softDeleteTemplates(ids: List<Long>)

    // ✅ Added for AppDatabase.kt
    @Query("SELECT COUNT(*) FROM exercise_templates")
    suspend fun getTemplateCount(): Int

    // --- Schedule Config ---
    @Query("SELECT * FROM schedule_config")
    fun getAllSchedules(): Flow<List<ScheduleConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(config: ScheduleConfig)

    @Query("SELECT * FROM schedule_config WHERE dayOfWeek = :day")
    suspend fun getScheduleConfigSync(day: Int): ScheduleConfig?

    // ✅ Added for AppDatabase.kt
    @Query("SELECT COUNT(*) FROM schedule_config")
    suspend fun getScheduleCount(): Int

    // --- Weekly Routine ---
    // ✅ Keep both suspend (for ViewModel/prepopulate) and non-suspend (if sync required, though risky)
    @Query("SELECT * FROM weekly_routine WHERE dayOfWeek = :day")
    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem>

    @Query("SELECT * FROM weekly_routine WHERE dayOfWeek = :day")
    fun getRoutineForDaySync(day: Int): List<WeeklyRoutineItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineItem(item: WeeklyRoutineItem)

    @Delete
    suspend fun deleteRoutineItem(item: WeeklyRoutineItem)

    @Query("DELETE FROM weekly_routine")
    suspend fun clearWeeklyRoutine()

    // --- Weight ---
    @Query("SELECT * FROM weight_records ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY date DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightRecord?>

    @Query("SELECT * FROM weight_records WHERE date = :date LIMIT 1")
    suspend fun getWeightForDate(date: String): WeightRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: WeightRecord)

    // --- History ---
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    fun getHistoryRecords(): Flow<List<WorkoutTask>>

    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    suspend fun getHistoryRecordsSync(): List<WorkoutTask>

    // For charts
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date ASC")
    fun getAllHistoryTasks(): Flow<List<WorkoutTask>>

    @Query("SELECT * FROM weight_records ORDER BY date ASC")
    fun getAllWeightRecords(): Flow<List<WeightRecord>>

    // --- App Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getAppSettings(): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(setting: AppSetting)
}