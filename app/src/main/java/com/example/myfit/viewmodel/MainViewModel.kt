package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.WorkoutDao
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import com.example.myfit.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao: WorkoutDao = database.workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // --- Theme & Language ---
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    // --- Schedule ---
    val allSchedules: Flow<List<ScheduleConfig>> = dao.getAllSchedules()

    private val _todayScheduleType = MutableStateFlow(DayType.CORE)
    val todayScheduleType = combine(_selectedDate, allSchedules) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        val type = schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.CORE
        _todayScheduleType.value = type
        type
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.CORE)

    // --- Weight Alert ---
    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // --- Data Flows ---
    val todayTasks: Flow<List<WorkoutTask>> = _selectedDate.flatMapLatest { date ->
        dao.getTasksForDate(date.toString())
    }

    val allTemplates: Flow<List<ExerciseTemplate>> = dao.getAllTemplates()
    val historyRecords: Flow<List<WorkoutTask>> = dao.getAllHistoryTasks()
    val weightHistory: Flow<List<WeightRecord>> = dao.getAllWeightRecords()

    // --- Timer State ---
    data class TimerState(
        val taskId: Long = -1L,
        val setIndex: Int = -1,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState = _timerState.asStateFlow()
    private var timerJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
    }

    // --- Timer Logic ---
    fun startTimer(context: Context, taskId: Long, setIndex: Int, durationMinutes: Int) {
        val current = _timerState.value
        val initialSeconds = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused) {
            current.remainingSeconds
        } else {
            if (durationMinutes <= 0) return
            durationMinutes * 60
        }

        _timerState.value = TimerState(taskId, setIndex, durationMinutes * 60, initialSeconds, true, false)

        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"

            val endTimeMillis = System.currentTimeMillis() + (initialSeconds * 1000)

            withContext(Dispatchers.Main) {
                NotificationHelper.updateTimerNotification(context, taskName, endTimeMillis)
            }

            while (_timerState.value.remainingSeconds > 0 && _timerState.value.isRunning) {
                try {
                    withContext(Dispatchers.Main) {
                        NotificationHelper.updateTimerNotification(context, taskName, endTimeMillis)
                    }
                } catch (e: Exception) { e.printStackTrace() }

                delay(1000)
                _timerState.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }

            if (_timerState.value.remainingSeconds <= 0 && _timerState.value.isRunning) {
                withContext(Dispatchers.Main) {
                    try { NotificationHelper.cancelNotification(context) } catch (e: Exception) { e.printStackTrace() }
                    onTimerFinished(taskId, setIndex, durationMinutes)
                }
            }
        }
    }

    fun pauseTimer(context: Context) {
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        timerJob?.cancel()
        try { NotificationHelper.updateTimerNotification(context, null, null) } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState()
        timerJob?.cancel()
        try { NotificationHelper.cancelNotification(context) } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun onTimerFinished(taskId: Long, setIndex: Int, durationMinutes: Int) {
        _timerState.value = TimerState()
        val task = dao.getTaskById(taskId) ?: return
        val newSets = task.sets.toMutableList()
        if (setIndex < newSets.size) {
            newSets[setIndex] = newSets[setIndex].copy(
                weightOrDuration = "${durationMinutes}min",
                reps = "Done"
            )
        }
        var updatedTask = task.copy(sets = newSets)
        val allSetsDone = newSets.all { it.weightOrDuration.isNotBlank() }

        if (allSetsDone) {
            updatedTask = updatedTask.copy(isCompleted = true)
        }
        dao.updateTask(updatedTask)

        withContext(Dispatchers.Main) {
            val app = getApplication<Application>()
            val msg = if (allSetsDone) {
                app.getString(R.string.toast_task_auto_completed)
            } else {
                app.getString(R.string.toast_set_completed)
            }
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Core Logic ---

    fun deleteTemplate(id: Long) = viewModelScope.launch {
        val t = dao.getTemplateById(id)
        if (t != null) dao.deleteTemplate(t)
    }

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t)
    }

    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        dao.insertTask(WorkoutTask(
            date = _selectedDate.value.toString(),
            templateId = t.id,
            name = t.name,
            category = t.category,
            target = t.defaultTarget,
            bodyPart = t.bodyPart,
            equipment = t.equipment,
            sets = listOf(WorkoutSet(1, "", ""))
        ))
    }

    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }

    fun applyWeeklyRoutineToToday() = viewModelScope.launch(Dispatchers.IO) {
        val dateStr = _selectedDate.value.toString()
        val dayOfWeek = _selectedDate.value.dayOfWeek.value
        val routineItems = dao.getRoutineForDaySync(dayOfWeek)
        routineItems.forEach { item ->
            dao.insertTask(WorkoutTask(
                date = dateStr,
                templateId = item.templateId,
                name = item.name,
                category = item.category,
                target = item.target,
                bodyPart = item.bodyPart,
                equipment = item.equipment,
                sets = listOf(WorkoutSet(1, "", ""))
            ))
        }
    }

    fun logWeight(w: Float) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w))
    }

    fun addRoutineItem(day: Int, template: ExerciseTemplate) = viewModelScope.launch {
        dao.insertRoutineItem(WeeklyRoutineItem(
            dayOfWeek = day,
            templateId = template.id,
            name = template.name,
            target = template.defaultTarget,
            category = template.category,
            bodyPart = template.bodyPart,
            equipment = template.equipment
        ))
    }

    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch {
        dao.deleteRoutineItem(item)
    }

    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(dayOfWeek = day, dayType = type))
        if (day == _selectedDate.value.dayOfWeek.value) _todayScheduleType.value = type
    }

    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem> = dao.getRoutineForDay(day)

    fun backupDatabase(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (database.isOpen) database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)
                if (dbPath.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(dbPath).use { input -> input.copyTo(output) } }
                    withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_backup_failed, e.message), Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun restoreDatabase(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)
                if (database.isOpen) database.close()
                context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(dbPath).use { output -> input.copyTo(output) } }
                val wal = File(dbPath.path + "-wal"); if (wal.exists()) wal.delete()
                val shm = File(dbPath.path + "-shm"); if (shm.exists()) shm.delete()
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_restore_success), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_restore_failed, e.message), Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return weightHistory.map { records ->
            val raw = records.map { Pair(LocalDate.parse(it.date), it.weight) }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                ChartDataPoint(date, list.map { it.second }.average().toFloat(), date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return historyRecords.map { tasks ->
            val cardio = tasks.filter { it.category == "CARDIO" }
            val raw = cardio.groupBy { LocalDate.parse(it.date) }.map { (date, tList) ->
                val sum = tList.sumOf { t ->
                    if (t.sets.isNotEmpty()) t.sets.sumOf { s -> parseDuration(s.weightOrDuration).toDouble() }
                    else parseDuration(t.target).toDouble()
                }.toFloat()
                Pair(date, sum)
            }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                ChartDataPoint(date, list.sumOf { it.second.toDouble() }.toFloat(), date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    fun getExerciseNamesByCategory(category: String): Flow<List<String>> {
        return historyRecords.map { tasks ->
            tasks.filter { it.category == category }.map { it.name }.distinct().sorted()
        }
    }

    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return historyRecords.map { tasks ->
            val targetTasks = tasks.filter { it.name == name }
            val raw = targetTasks.groupBy { LocalDate.parse(it.date) }.map { (date, tList) ->
                val values = tList.flatMap { t -> if (t.sets.isNotEmpty()) t.sets else listOf(WorkoutSet(1, t.actualWeight.ifEmpty { t.target }, t.target)) }
                val dailyVal = when(mode) {
                    0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat()
                    1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f
                    2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat()
                    else -> 0f
                }
                Pair(date, dailyVal)
            }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                val finalVal = if (mode == 1) list.map { it.second }.average().toFloat() else list.sumOf { it.second.toDouble() }.toFloat()
                ChartDataPoint(date, finalVal, date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    private fun parseValue(input: String): Float {
        val regex = Regex("[0-9]+(\\.[0-9]+)?")
        return regex.find(input)?.value?.toFloatOrNull() ?: 0f
    }

    private fun parseDuration(input: String): Float {
        val lower = input.lowercase()
        val num = parseValue(lower)
        return when {
            lower.contains("h") -> num * 60
            lower.contains("s") && !lower.contains("m") -> num / 60
            else -> num
        }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        val currentLang = currentLanguage.value
        dao.saveAppSettings(AppSetting(0, theme.id, currentLang))
    }
    fun switchLanguage(lang: String) = viewModelScope.launch {
        val currentThemeId = currentTheme.value.id
        dao.saveAppSettings(AppSetting(0, currentThemeId, lang))
    }
    fun exportHistoryToCsv(context: Context) {}
    fun importWeeklyRoutine(context: Context, csv: String) {}
    suspend fun optimizeExerciseLibrary(): Int = 0
}