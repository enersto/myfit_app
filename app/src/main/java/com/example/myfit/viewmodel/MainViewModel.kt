package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.WorkoutDao
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import com.example.myfit.util.NotificationHelper
import com.example.myfit.util.TimerService
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
import kotlin.system.exitProcess

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao: WorkoutDao = database.workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // --- User Profile ---
    val userProfile = dao.getAppSettings()
        .map { it ?: AppSetting(themeId = 1) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSetting(themeId = 1))

    // --- Theme & Language ---
    val currentTheme = userProfile
        .map { AppTheme.fromId(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.GREEN)

    val currentLanguage = userProfile
        .map { it.languageCode }
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

    // --- Lock Screen Guide State ---
    private val prefs = application.getSharedPreferences("myfit_prefs", Context.MODE_PRIVATE)
    private val _hasShownLockScreenGuide = MutableStateFlow(prefs.getBoolean("key_lockscreen_guide_shown", false))
    val hasShownLockScreenGuide = _hasShownLockScreenGuide.asStateFlow()

    fun markLockScreenGuideShown() {
        prefs.edit().putBoolean("key_lockscreen_guide_shown", true).apply()
        _hasShownLockScreenGuide.value = true
    }

    // --- Timer State ---
    data class TimerState(
        val taskId: Long = -1L,
        val setIndex: Int = -1,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val endTimeMillis: Long = 0L,
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
        val now = System.currentTimeMillis()
        val durationMillis = durationMinutes * 60 * 1000L

        val endTimeMillis = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused) {
            now + (current.remainingSeconds * 1000L)
        } else {
            if (durationMinutes <= 0) return
            now + durationMillis
        }

        val initialRemSeconds = ((endTimeMillis - now) / 1000).toInt()
        _timerState.value = TimerState(taskId, setIndex, durationMinutes * 60, initialRemSeconds, endTimeMillis, true, false)

        viewModelScope.launch(Dispatchers.IO) {
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_TASK_NAME, taskName)
                putExtra(TimerService.EXTRA_END_TIME, endTimeMillis)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"

            while (_timerState.value.isRunning) {
                val currentNow = System.currentTimeMillis()
                val targetEnd = _timerState.value.endTimeMillis
                val remSeconds = ((targetEnd - currentNow) / 1000).toInt()

                if (remSeconds <= 0) {
                    _timerState.update { it.copy(remainingSeconds = 0) }
                    withContext(Dispatchers.Main) {
                        stopService(context)
                        onTimerFinished(taskId, setIndex, durationMinutes)
                    }
                    break
                } else {
                    _timerState.update { it.copy(remainingSeconds = remSeconds) }
                    try {
                        withContext(Dispatchers.Main) {
                            NotificationHelper.updateTimerNotification(context, taskName, targetEnd)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                delay(500)
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
        stopService(context)
    }

    private fun stopService(context: Context) {
        try {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationHelper.cancelNotification(context)
        }
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

    // --- Chart Data Logic ---
    private fun calculateBMI(weight: Float, heightCm: Float): Float {
        if (heightCm <= 0) return 0f
        val heightM = heightCm / 100f
        return weight / (heightM * heightM)
    }

    private fun calculateBMR(weight: Float, heightCm: Float, age: Int, gender: Int): Float {
        if (heightCm <= 0 || age <= 0) return 0f
        val s = if (gender == 0) 5 else -161
        return (10 * weight) + (6.25f * heightCm) - (5 * age) + s
    }

    fun getBMIChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return combine(weightHistory, userProfile) { weights, profile ->
            val raw = weights.map {
                val bmi = calculateBMI(it.weight, profile.height)
                Pair(LocalDate.parse(it.date), bmi)
            }
            groupAndFormatData(raw, granularity)
        }
    }

    fun getBMRChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return combine(weightHistory, userProfile) { weights, profile ->
            val raw = weights.map {
                val bmr = calculateBMR(it.weight, profile.height, profile.age, profile.gender)
                Pair(LocalDate.parse(it.date), bmr)
            }
            groupAndFormatData(raw, granularity)
        }
    }

    private fun groupAndFormatData(raw: List<Pair<LocalDate, Float>>, granularity: ChartGranularity): List<ChartDataPoint> {
        val grouped = when (granularity) {
            ChartGranularity.DAILY -> raw.groupBy { it.first }
            ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
        }
        return grouped.map { (date, list) ->
            ChartDataPoint(
                date,
                list.map { it.second }.average().toFloat(),
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            )
        }.sortedBy { it.date }
    }

    // --- Actions ---

    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        val currentSettings = userProfile.value
        dao.saveAppSettings(currentSettings.copy(themeId = theme.id))
    }

    fun switchLanguage(lang: String) = viewModelScope.launch {
        val currentSettings = userProfile.value
        dao.saveAppSettings(currentSettings.copy(languageCode = lang))
    }

    fun logWeightAndProfile(weight: Float, age: Int?, height: Float?, gender: Int?) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = weight))

        val currentSettings = userProfile.value
        val newSettings = currentSettings.copy(
            age = age ?: currentSettings.age,
            height = height ?: currentSettings.height,
            gender = gender ?: currentSettings.gender
        )
        dao.saveAppSettings(newSettings)
    }

    fun updateProfile(age: Int, height: Float, gender: Int) = viewModelScope.launch {
        val currentSettings = userProfile.value
        dao.saveAppSettings(currentSettings.copy(age = age, height = height, gender = gender))
    }

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

    fun exportHistoryToCsv(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tasks = dao.getHistoryRecordsSync()
                val sb = StringBuilder()
                sb.append("Date,Name,Category,Target,ActualWeight,Sets\n")

                tasks.forEach { t ->
                    val safeName = t.name.replace(",", " ")
                    val setsStr = t.sets.joinToString(" | ") { "${it.weightOrDuration} x ${it.reps}" }
                    sb.append("${t.date},$safeName,${t.category},${t.target},${t.actualWeight},$setsStr\n")
                }

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(sb.toString().toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun backupDatabase(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 强制 WAL checkpoint
                if (database.isOpen) {
                    val db = database.openHelper.writableDatabase
                    db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                    // ✅ 确保 checkpoint 完成
                    db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                }

                // ✅ 2. 增加等待时间
                delay(1000)

                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                if (dbPath.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(dbPath).use { input ->
                            input.copyTo(output)
                            // ✅ 强制刷新输出流
                            output.flush()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_backup_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    throw Exception("Database file not found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_backup_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 🔴 [核心修复] 彻底解决恢复数据不全的问题
    fun restoreDatabase(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                // 1. 关闭数据库连接
                if (database.isOpen) database.close()

                // 2. 覆盖主数据库文件
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                        // ✅ 强制刷新到磁盘
                        output.fd.sync()
                    }
                }

                // 3. 删除旧的 WAL/SHM 文件
                val walFile = File(dbPath.path + "-wal")
                val shmFile = File(dbPath.path + "-shm")
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                // ✅ 4. 等待文件系统完成所有写入操作
                delay(1000)  // 增加到 1 秒确保安全

                // ✅ 5. 验证文件完整性（可选但推荐）
                if (!dbPath.exists() || dbPath.length() < 1024) {
                    throw Exception("Database file is incomplete after restore")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_restore_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    // 6. 最后才重启
                    triggerRestart(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_restore_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun triggerRestart(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return weightHistory.map { records ->
            val raw = records.map { Pair(LocalDate.parse(it.date), it.weight) }
            groupAndFormatData(raw, granularity)
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
            groupAndFormatData(raw, granularity)
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
            groupAndFormatData(raw, granularity)
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

    fun importWeeklyRoutine(context: Context, csv: String) {}
    suspend fun optimizeExerciseLibrary(): Int = 0
}