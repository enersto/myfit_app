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

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.REST
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.REST)

    val todayTasks = _selectedDate.flatMapLatest { dao.getTasksForDate(it.toString()) }
    val historyRecords = dao.getHistoryRecords()
    val weightHistory = dao.getAllWeights()
    val allTemplates = dao.getAllTemplates()

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun switchLanguage(code: String) {
        viewModelScope.launch {
            val currentSetting = dao.getAppSettings().firstOrNull()
            val themeId = currentSetting?.themeId ?: 0
            dao.saveAppSettings(AppSetting(id = 0, themeId = themeId, languageCode = code))
        }
    }

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
                Toast.makeText(getApplication(), "No Routine Found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            routineItems.forEach { item ->
                // 这里我们尽量去匹配 Template 获取最新的部位信息，如果找不到就用默认空
                val template = dao.getTemplateById(item.templateId)
                dao.insertTask(WorkoutTask(
                    date = date.toString(),
                    templateId = item.templateId,
                    name = item.name,
                    category = item.category,
                    bodyPart = template?.bodyPart ?: "",
                    equipment = template?.equipment ?: "",
                    // 如果有预设目标，生成一个默认的空组作为提示
                    sets = listOf(WorkoutSet(1, "", "")),
                    target = item.target
                ))
            }
            Toast.makeText(getApplication(), "Routine Applied", Toast.LENGTH_SHORT).show()
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
                            val catStr = parts[2].uppercase()
                            val category = when {
                                catStr.contains("有氧") || catStr.contains("CARDIO") -> "CARDIO"
                                catStr.contains("核心") || catStr.contains("CORE") -> "CORE"
                                else -> "STRENGTH"
                            }
                            dao.insertRoutineItem(WeeklyRoutineItem(dayOfWeek = day, templateId = 0, name = parts[1], category = category, target = parts[3]))
                            count++
                        }
                    }
                }
                Toast.makeText(getApplication(), "Imported $count items", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun exportHistoryToCsv(context: Context) {
        viewModelScope.launch {
            val records = dao.getHistoryRecordsSync()
            val sb = StringBuilder().append("Date,Name,Category,BodyPart,Equipment,Sets\n")
            records.forEach {
                // 简单的把 sets 转为字符串导出
                val setsStr = it.sets.joinToString(" | ") { s -> "${s.weightOrDuration}x${s.reps}" }
                sb.append("${it.date},${it.name},${it.category},${it.bodyPart},${it.equipment},\"$setsStr\"\n")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, sb.toString()); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "history.csv")
            }
            context.startActivity(Intent.createChooser(intent, "Export CSV"))
        }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        val currentSetting = dao.getAppSettings().firstOrNull()
        val lang = currentSetting?.languageCode ?: "zh"
        dao.saveAppSettings(AppSetting(id = 0, themeId = theme.id, languageCode = lang))
    }

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch { if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t) }
    fun deleteTemplate(id: Long) = viewModelScope.launch { dao.softDeleteTemplate(id) }

    // V5.0 修复：从模板添加任务时，复制所有新字段
    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        dao.insertTask(WorkoutTask(
            date = _selectedDate.value.toString(),
            templateId = t.id,
            name = t.name,
            category = t.category,
            bodyPart = t.bodyPart,
            equipment = t.equipment,
            target = t.defaultTarget,
            // 默认添加第一组空数据方便输入
            sets = listOf(WorkoutSet(1, "", ""))
        ))
    }

    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }
    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch { dao.insertSchedule(ScheduleConfig(day, type)) }
    fun logWeight(w: Float) = viewModelScope.launch { dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w)) }
    suspend fun getRoutineForDay(day: Int) = dao.getRoutineForDay(day)
}