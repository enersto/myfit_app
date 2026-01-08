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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao: WorkoutDao = database.workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // --- Theme & Language ---
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 1 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    // --- User Profile (New) ---
    val userProfile = dao.getAppSettings()
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSetting()) // 默认空对象


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

    // --- Chart Data Logic (New for BMI/BMR) ---

    // 计算 BMI: kg / (m^2)
    private fun calculateBMI(weight: Float, heightCm: Float): Float {
        if (heightCm <= 0) return 0f
        val heightM = heightCm / 100f
        return weight / (heightM * heightM)
    }

    // 计算 BMR (Mifflin-St Jeor 公式)
    private fun calculateBMR(weight: Float, heightCm: Float, age: Int, gender: Int): Float {
        // Gender: 0=Male, 1=Female
        if (heightCm <= 0 || age <= 0) return 0f
        val s = if (gender == 0) 5 else -161
        return (10 * weight) + (6.25f * heightCm) - (5 * age) + s
    }

    // 4) 获取 BMI 图表数据
    // 注意：历史记录里没有存当时的身高，所以我们使用当前的身高来估算历史 BMI/BMR
    // 这是一个常见的简化处理。
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

    // 辅助函数：处理图表数据分组和格式化 (复用原有的逻辑)
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

    // 2) & 3) 更新 LogWeight 逻辑：同时更新用户信息
    fun logWeightAndProfile(weight: Float, age: Int?, height: Float?, gender: Int?) = viewModelScope.launch {
        // 1. 记录体重
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = weight))

        // 2. 更新 Profile (如果有输入)
        val currentSettings = userProfile.value
        val newSettings = currentSettings.copy(
            age = age ?: currentSettings.age,
            height = height ?: currentSettings.height,
            gender = gender ?: currentSettings.gender
        )
        dao.saveAppSettings(newSettings)
    }

    // 单独更新 Profile (用于设置页面)
    fun updateProfile(age: Int, height: Float, gender: Int) = viewModelScope.launch {
        val currentSettings = userProfile.value
        dao.saveAppSettings(currentSettings.copy(age = age, height = height, gender = gender))
    }

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
        // [修复] 确保在 App 启动时就创建好通道
        NotificationHelper.createNotificationChannel(application)
    }

    // [新增] SharedPreferences 用于保存一些一次性的 UI 状态
    private val prefs = application.getSharedPreferences("myfit_prefs", Context.MODE_PRIVATE)

    // [新增] 状态流：是否已经展示过锁屏引导
    private val _hasShownLockScreenGuide = MutableStateFlow(prefs.getBoolean("key_lockscreen_guide_shown", false))
    val hasShownLockScreenGuide = _hasShownLockScreenGuide.asStateFlow()

    // --- Timer Logic (Updated for Foreground Service) ---
    // [新增] 标记为已展示（下次不再弹）
    fun markLockScreenGuideShown() {
        prefs.edit().putBoolean("key_lockscreen_guide_shown", true).apply()
        _hasShownLockScreenGuide.value = true
    }

    fun startTimer(context: Context, taskId: Long, setIndex: Int, durationMinutes: Int) {
        val current = _timerState.value
        val initialSeconds = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused) {
            current.remainingSeconds
        } else {
            if (durationMinutes <= 0) return
            durationMinutes * 60
        }

        // 更新 UI 状态
        _timerState.value = TimerState(taskId, setIndex, durationMinutes * 60, initialSeconds, true, false)

        // 计算结束时间
        val endTimeMillis = System.currentTimeMillis() + (initialSeconds * 1000)

        // [新增] 启动前台服务 Service
        // 这一步是将 App 提升为前台进程的关键，确保锁屏可见
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

        // 启动协程：仅用于 UI 倒计时更新 和 刷新 Notification 时间文本
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            // 获取 TaskName 用于 updateTimerNotification
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"

            while (_timerState.value.remainingSeconds > 0 && _timerState.value.isRunning) {
                try {
                    // 刷新通知栏上的倒计时文字
                    withContext(Dispatchers.Main) {
                        NotificationHelper.updateTimerNotification(context, taskName, endTimeMillis)
                    }
                } catch (e: Exception) { e.printStackTrace() }

                delay(1000)
                _timerState.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }

            // 倒计时结束
            if (_timerState.value.remainingSeconds <= 0 && _timerState.value.isRunning) {
                withContext(Dispatchers.Main) {
                    stopService(context) // 停止服务
                    onTimerFinished(taskId, setIndex, durationMinutes)
                }
            }
        }
    }

    fun pauseTimer(context: Context) {
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        timerJob?.cancel()

        // 暂停时，我们停止前台服务（因为不再是活跃计时），或者你可以选择保留服务但更新通知为“已暂停”
        // 这里选择发送一个 updateTimerNotification 将通知变为 "Paused" 状态
        // 注意：如果要长期暂停并保持锁屏显示，Service 应该继续运行。但通常暂停意味着用户在操作手机。
        // 为了省电和逻辑简单，这里仅更新 UI。
        // 若要更严谨，可以向 Service 发送 ACTION_PAUSE (如果实现了的话)，或者就在这里更新 Notification：
        try { NotificationHelper.updateTimerNotification(context, null, null) } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState()
        timerJob?.cancel()
        stopService(context)
    }

    // [新增] 辅助函数：停止服务
    private fun stopService(context: Context) {
        try {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // 兜底：直接取消通知
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