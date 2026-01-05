package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.REST
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.REST)

    val todayTasks = _selectedDate.flatMapLatest { dao.getTasksForDate(it.toString()) }

    val historyRecords = dao.getHistoryRecords()

    // ▼▼▼ V4.2 新增：暴露所有体重记录 ▼▼▼
    val weightHistory = dao.getAllWeights()
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

    val allTemplates = dao.getAllTemplates()

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // --- V4.1 功能保持不变 ---
    fun addRoutineItem(dayOfWeek: Int, template: ExerciseTemplate) {
        viewModelScope.launch {
            dao.insertRoutineItem(WeeklyRoutineItem(dayOfWeek = dayOfWeek, templateId = template.id, name = template.name, target = template.defaultTarget, category = template.category))
        }
    }
    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch { dao.deleteRoutineItem(item) }

    fun applyWeeklyRoutineToToday() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val routineItems = dao.getRoutineForDay(date.dayOfWeek.value)
            if (routineItems.isEmpty()) {
                Toast.makeText(getApplication(), "今日暂无预设方案", Toast.LENGTH_SHORT).show()
                return@launch
            }
            routineItems.forEach { item ->
                dao.insertTask(WorkoutTask(date = date.toString(), templateId = item.templateId, name = item.name, target = item.target, type = item.category))
            }
            Toast.makeText(getApplication(), "已应用今日方案", Toast.LENGTH_SHORT).show()
        }
    }

    fun importWeeklyRoutine(csvContent: String) {
        viewModelScope.launch {
            try {
                dao.clearWeeklyRoutine()
                val lines = csvContent.lines()
                var count = 0
                lines.forEach { line ->
                    val parts = line.replace("，", ",").trim().split(",").map { it.trim() }
                    if (parts.size >= 4) {
                        val day = parts[0].toIntOrNull()
                        if (day != null) {
                            dao.insertRoutineItem(WeeklyRoutineItem(dayOfWeek = day, templateId = 0, name = parts[1], category = if (parts[2].contains("有氧")) "CARDIO" else "STRENGTH", target = parts[3]))
                            count++
                        }
                    }
                }
                Toast.makeText(getApplication(), "成功导入 $count 条", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(getApplication(), "失败: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun exportHistoryToCsv(context: Context) {
        viewModelScope.launch {
            val records = dao.getHistoryRecordsSync()
            val sb = StringBuilder().append("日期,动作,目标,实测,类型\n")
            records.forEach { sb.append("${it.date},${it.name},${it.target},${it.actualWeight},${it.type}\n") }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, sb.toString()); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "历史.csv")
            }
            context.startActivity(Intent.createChooser(intent, "导出CSV"))
        }
    }

    // 原有基础方法
    fun switchTheme(theme: AppTheme) = viewModelScope.launch { dao.saveAppSettings(AppSetting(themeId = theme.id)) }
    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch { if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t) }
    fun deleteTemplate(id: Long) = viewModelScope.launch { dao.softDeleteTemplate(id) }
    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch { dao.insertTask(WorkoutTask(date = _selectedDate.value.toString(), templateId = t.id, name = t.name, target = t.defaultTarget, type = t.category)) }
    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }
    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch { dao.insertSchedule(ScheduleConfig(day, type)) }
    fun logWeight(w: Float) = viewModelScope.launch { dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w)) }
    suspend fun getRoutineForDay(day: Int) = dao.getRoutineForDay(day)
}