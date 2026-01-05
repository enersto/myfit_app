package com.example.myfit.data

import androidx.room.*
import com.example.myfit.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getAppSettings(): Flow<AppSetting?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(setting: AppSetting)

    @Query("SELECT * FROM schedule_config ORDER BY dayOfWeek ASC")
    fun getAllSchedules(): Flow<List<ScheduleConfig>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(config: ScheduleConfig)
    @Query("SELECT COUNT(*) FROM schedule_config")
    suspend fun getScheduleCount(): Int

    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllTemplates(): Flow<List<ExerciseTemplate>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplate): Long
    @Update
    suspend fun updateTemplate(template: ExerciseTemplate)
    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteTemplate(id: Long)
    @Query("SELECT COUNT(*) FROM exercise_templates")
    suspend fun getTemplateCount(): Int

    @Query("SELECT * FROM workout_tasks WHERE date = :date")
    fun getTasksForDate(date: String): Flow<List<WorkoutTask>>
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    fun getHistoryRecords(): Flow<List<WorkoutTask>>
    // 用于导出 CSV 的同步查询
    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    suspend fun getHistoryRecordsSync(): List<WorkoutTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkoutTask)
    @Update
    suspend fun updateTask(task: WorkoutTask)
    @Delete
    suspend fun deleteTask(task: WorkoutTask)

    @Query("SELECT * FROM weight_records ORDER BY date DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightRecord?>

    // ▼▼▼ V4.2 新增：获取所有体重记录，用于历史页展示 ▼▼▼
    @Query("SELECT * FROM weight_records ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeightRecord>>
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

    @Insert
    suspend fun insertWeight(record: WeightRecord)

    // 周计划相关
    @Query("SELECT * FROM weekly_routine WHERE dayOfWeek = :dayOfWeek")
    suspend fun getRoutineForDay(dayOfWeek: Int): List<WeeklyRoutineItem>
    @Query("DELETE FROM weekly_routine")
    suspend fun clearWeeklyRoutine()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineItem(item: WeeklyRoutineItem)
    @Delete
    suspend fun deleteRoutineItem(item: WeeklyRoutineItem)
}