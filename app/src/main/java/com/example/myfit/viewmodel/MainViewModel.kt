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
import android.os.SystemClock // 关键：使用系统运行时间
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.SharedPreferences

import com.google.gson.Gson // [新增]
import com.google.gson.reflect.TypeToken // [新增]


// [新增] 计时器阶段枚举
enum class TimerPhase {
    IDLE, PREP, WORK
}

// [新增] 用于热力图的数据类 (Intensity=0.0~1.0 用于颜色, Volume=原始容量用于显示)
data class HeatmapPoint(val intensity: Float, val volume: Float)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao: WorkoutDao = database.workoutDao()

    // [新增] 计时器偏好设置 Key
    companion object {
        const val KEY_TIMER_PREP_ENABLED = "timer_prep_enabled"
        const val KEY_TIMER_PREP_SECS = "timer_prep_secs"
        const val KEY_TIMER_FINAL_ENABLED = "timer_final_enabled"
        const val KEY_TIMER_FINAL_SECS = "timer_final_secs"
        const val KEY_TIMER_SOUND_ENABLED = "timer_sound_enabled" // [新增]
    }

    // [新增] 声音生成器 (音量 100)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    // [新增] 读取和保存设置的辅助方法
    fun getTimerPrepEnabled() = prefs.getBoolean(KEY_TIMER_PREP_ENABLED, true)
    fun setTimerPrepEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_PREP_ENABLED, enable).apply()

    fun getTimerPrepSeconds() = prefs.getInt(KEY_TIMER_PREP_SECS, 10)
    fun setTimerPrepSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_PREP_SECS, secs).apply()

    fun getTimerFinalEnabled() = prefs.getBoolean(KEY_TIMER_FINAL_ENABLED, true)
    fun setTimerFinalEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_FINAL_ENABLED, enable).apply()

    fun getTimerFinalSeconds() = prefs.getInt(KEY_TIMER_FINAL_SECS, 5)
    fun setTimerFinalSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_FINAL_SECS, secs).apply()

    // [新增] 音效开关读写方法
    fun getTimerSoundEnabled() = prefs.getBoolean(KEY_TIMER_SOUND_ENABLED, true)
    fun setTimerSoundEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_SOUND_ENABLED, enable).apply()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val userProfile = dao.getAppSettings()
        .map { it ?: AppSetting(themeId = 1) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSetting(themeId = 1))

    val currentTheme = userProfile
        .map { AppTheme.fromId(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.GREEN)

    val currentLanguage = userProfile
        .map { it.languageCode }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    val allSchedules: Flow<List<ScheduleConfig>> = dao.getAllSchedules()

    private val _todayScheduleType = MutableStateFlow(DayType.CORE)
    val todayScheduleType = combine(_selectedDate, allSchedules) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        val type = schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.CORE
        _todayScheduleType.value = type
        type
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.CORE)

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val todayTasks: Flow<List<WorkoutTask>> = _selectedDate.flatMapLatest { date ->
        dao.getTasksForDate(date.toString())
    }

    val allTemplates: Flow<List<ExerciseTemplate>> = dao.getAllTemplates()
    val historyRecords: Flow<List<WorkoutTask>> = dao.getAllHistoryTasks()
    val weightHistory: Flow<List<WeightRecord>> = dao.getAllWeightRecords()

    private val prefs = application.getSharedPreferences("myfit_prefs", Context.MODE_PRIVATE)
    private val _hasShownLockScreenGuide = MutableStateFlow(prefs.getBoolean("key_lockscreen_guide_shown", false))
    val hasShownLockScreenGuide = _hasShownLockScreenGuide.asStateFlow()

    // [修改] 肌肉热力图数据：返回 HeatmapPoint (包含 intensity 和 raw volume)
    val muscleHeatMapData: Flow<Map<String, HeatmapPoint>> = historyRecords.map { tasks ->
        val currentVolumeMap = mutableMapOf<String, Float>()

        fun parseVal(input: String): Float =
            Regex("[0-9]+(\\.[0-9]+)?").find(input)?.value?.toFloatOrNull() ?: 0f

        tasks.forEach { task ->
            var taskVolume = 0f

            if (task.sets.isNotEmpty()) {
                task.sets.forEach { set ->
                    val w = parseVal(set.weightOrDuration).coerceAtLeast(1f)
                    val r = parseVal(set.reps).coerceAtLeast(1f)
                    taskVolume += (w * r)

                    if (task.isUnilateral) {
                        val rw = parseVal(set.rightWeight ?: "0")
                        val rr = parseVal(set.rightReps ?: "0")
                        if (rw > 0 && rr > 0) {
                            taskVolume += (rw * rr)
                        }
                    }
                }
            } else {
                val targetVal = parseVal(task.target)
                taskVolume += if (targetVal > 0) targetVal else 1f
            }

            currentVolumeMap[task.bodyPart] = currentVolumeMap.getOrDefault(task.bodyPart, 0f) + taskVolume
        }

        val globalMaxVolume = currentVolumeMap.values.maxOrNull() ?: 1f

        currentVolumeMap.mapValues { (_, vol) ->
            // HeatmapPoint: (热度比例 0~1, 原始容量值)
            HeatmapPoint(
                intensity = (vol / globalMaxVolume).coerceIn(0f, 1f),
                volume = vol
            )
        }
    }

    fun markLockScreenGuideShown() {
        prefs.edit().putBoolean("key_lockscreen_guide_shown", true).apply()
        _hasShownLockScreenGuide.value = true
    }

    data class TimerState(
        val taskId: Long = -1L,
        val setIndex: Int = -1,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val endTimeMillis: Long = 0L, // 这里将存储 elapsedRealtime
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val phase: TimerPhase = TimerPhase.IDLE, // 新增：当前阶段
        val showBigAlert: Boolean = false // 新增：是否显示大弹窗
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState = _timerState.asStateFlow()
    private var timerJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
    }

    // [重构] 启动计时器入口：决定是进入准备阶段还是直接开始
    fun startTimer(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val prepEnabled = getTimerPrepEnabled()
        // 只有当前是空闲状态，且开启了准备时间，才进入 PREP 阶段
        if (prepEnabled && _timerState.value.phase == TimerPhase.IDLE) {
            startPrepPhase(context, taskId, setIndex, durationMinutes)
        } else {
            startWorkPhase(context, taskId, setIndex, durationMinutes)
        }
    }

    // [新增] 启动准备阶段
    private fun startPrepPhase(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val prepSeconds = getTimerPrepSeconds()
        val now = SystemClock.elapsedRealtime() // 【核心】使用精准时间
        val endTime = now + (prepSeconds * 1000L)

        _timerState.value = TimerState(
            taskId = taskId,
            setIndex = setIndex,
            totalSeconds = prepSeconds,
            remainingSeconds = prepSeconds,
            endTimeMillis = endTime,
            isRunning = true,
            phase = TimerPhase.PREP,
            showBigAlert = true // 准备阶段强制显示大弹窗
        )

        // 准备阶段的回调：倒计时结束后，自动调用 startWorkPhase
        runTimerLoop(context, isPrep = true) {
            startWorkPhase(context, taskId, setIndex, durationMinutes)
        }
    }

    // [重构] 启动正式训练阶段 (对应原本的 startTimer 逻辑)
    private fun startWorkPhase(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val current = _timerState.value
        val now = SystemClock.elapsedRealtime() // 【核心】使用精准时间

        // [修改] 计算毫秒数 (支持小数分钟，例如 0.5 * 60 * 1000 = 30000)
        val durationMillis = (durationMinutes * 60 * 1000).toLong()

        // 判断是否是“暂停后继续”：任务ID一致、Set一致、处于暂停状态、且之前是在WORK阶段暂停的
        val endTimeMillis = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused && current.phase == TimerPhase.WORK) {
            now + (current.remainingSeconds * 1000L)
        } else {
            now + durationMillis
        }

        val initialRemSeconds = ((endTimeMillis - now) / 1000).toInt()

        _timerState.value = TimerState(
            taskId, setIndex, (durationMinutes * 60).toInt(), initialRemSeconds, endTimeMillis,
            isRunning = true, isPaused = false, phase = TimerPhase.WORK, showBigAlert = false
        )

        // 启动后台服务 (通知栏显示)
        viewModelScope.launch(Dispatchers.IO) {
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"

            // 为了兼容 Service (通常使用墙钟时间)，我们需要换算一下
            val wallClockEndTime = System.currentTimeMillis() + (endTimeMillis - now)

            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_TASK_NAME, taskName)
                putExtra(TimerService.EXTRA_END_TIME, wallClockEndTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // 正式阶段的回调：倒计时结束后，记录数据
        runTimerLoop(context, isPrep = false) {
            onTimerFinished(taskId, setIndex, durationMinutes)
        }
    }

    // [重构] 统一的计时循环逻辑
    private fun runTimerLoop(context: Context, isPrep: Boolean, onFinish: suspend () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val finalEnabled = getTimerFinalEnabled()
            val finalSeconds = getTimerFinalSeconds()

            var lastBeepSecond = -1 // 防止同一秒响两次

            // [新增] 获取当前音效开关状态
            val soundEnabled = getTimerSoundEnabled()

            while (_timerState.value.isRunning) {
                val currentNow = SystemClock.elapsedRealtime()
                val targetEnd = _timerState.value.endTimeMillis
                // +1 是为了补偿取整，让 0.9秒 显示为 1秒，体验更佳
                val remSeconds = ((targetEnd - currentNow) / 1000).toInt() + 1

                // 如果时间已到 (<=0)，修正显示为 0
                val displaySeconds = if ((targetEnd - currentNow) <= 0) 0 else remSeconds

                // --- 判断是否显示大弹窗和播放声音 ---
                // 条件：是准备阶段，或者 (是正式阶段 且 开启了倒数 且 时间进入倒数范围)
                val isFinalCountdown = displaySeconds > 0 && displaySeconds <= if (isPrep) 3 else finalSeconds

                _timerState.update {
                    it.copy(
                        remainingSeconds = displaySeconds,
                        showBigAlert = isPrep || (finalEnabled && isFinalCountdown)
                    )
                }

                // --- 播放声音 ---

                if (soundEnabled && displaySeconds != lastBeepSecond && isFinalCountdown) {
                    try {
                        // 发出短促的“滴”声
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    } catch (e: Exception) { e.printStackTrace() }
                    lastBeepSecond = displaySeconds
                }

                // --- 时间结束 ---
                if ((targetEnd - currentNow) <= 0) {
                    // 结束时的长音“嘟——”
                    // [修改] 增加 soundEnabled 判断
                    if (soundEnabled) {
                        try {
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    try {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    } catch (e: Exception) { e.printStackTrace() }

                    _timerState.update { it.copy(remainingSeconds = 0, isRunning = false, showBigAlert = false) }

                    withContext(Dispatchers.Main) {
                        if (!isPrep) stopService(context) // 只有正式结束才关 Service
                        onFinish() // 执行回调
                    }
                    break
                }

                // 提高刷新频率到 100ms，保证倒计时平滑
                delay(100)
            }
        }
    }

    fun pauseTimer(context: Context) {
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        timerJob?.cancel()
        try { NotificationHelper.updateTimerNotification(context, null, null) } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState(phase = TimerPhase.IDLE)
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

    // [新增/修改]
    override fun onCleared() {
        super.onCleared()
        toneGenerator.release() // 释放音频资源防止内存泄漏
    }

    private suspend fun onTimerFinished(taskId: Long, setIndex: Int, durationMinutes: Float) {
        _timerState.value = TimerState()
        val task = dao.getTaskById(taskId) ?: return
        val newSets = task.sets.toMutableList()
        if (setIndex < newSets.size) {
            // [修改] 格式化时间字符串：如果是整数则不显示小数位 (1.0 -> 1min, 0.5 -> 0.5min)
            val timeStr = if (durationMinutes % 1.0f == 0f) {
                "${durationMinutes.toInt()}min"
            } else {
                "${durationMinutes}min"
            }

            newSets[setIndex] = newSets[setIndex].copy(
                weightOrDuration = timeStr,
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
            isUnilateral = t.isUnilateral,
            logType = t.logType, // [新增] 传递 logType
            // [修复] 显式指定参数名，消除歧义
            sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = ""))
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
                isUnilateral = item.isUnilateral,
                logType = item.logType, // [新增] 传递 logType
                // [修复] 显式指定参数名
                sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = ""))
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
            equipment = template.equipment,
            isUnilateral = template.isUnilateral,
            logType = template.logType // [新增]
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
                sb.append("Date,Name,Category,Target,IsUnilateral,LogType,ActualWeight,Sets\n")

                tasks.forEach { t ->
                    val safeName = t.name.replace(",", " ")
                    val setsStr = t.sets.joinToString(" | ") { set ->
                        if (t.isUnilateral) {
                            val left = "${set.weightOrDuration} x ${set.reps}"
                            val right = "${set.rightWeight ?: ""} x ${set.rightReps ?: ""}"
                            "L: $left / R: $right"
                        } else {
                            "${set.weightOrDuration} x ${set.reps}"
                        }
                    }
                    sb.append("${t.date},$safeName,${t.category},${t.target},${t.isUnilateral},${t.logType},${t.actualWeight},$setsStr\n")
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
                if (database.isOpen) {
                    val db = database.openHelper.writableDatabase
                    db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                    db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                }
                delay(1000)

                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                if (dbPath.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(dbPath).use { input ->
                            input.copyTo(output)
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

    fun restoreDatabase(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                if (database.isOpen) database.close()

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }

                val walFile = File(dbPath.path + "-wal")
                val shmFile = File(dbPath.path + "-shm")
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                delay(1000)

                if (!dbPath.exists() || dbPath.length() < 1024) {
                    throw Exception("Database file is incomplete after restore")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_restore_success),
                        Toast.LENGTH_SHORT
                    ).show()
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

    // [局部替换] 支持 "mode=3" 获取右侧重量数据
    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return historyRecords.map { tasks ->
            val targetTasks = tasks.filter { it.name == name }
            val raw = targetTasks.groupBy { LocalDate.parse(it.date) }.map { (date, tList) ->
                val values = tList.flatMap { t ->
                    if (t.sets.isNotEmpty()) t.sets
                    else listOf(WorkoutSet(setNumber = 1, weightOrDuration = t.actualWeight.ifEmpty { t.target }, reps = t.target))
                }

                val dailyVal = when(mode) {
                    0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat() // 有氧时长
                    1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f // 力量：左边/双边 重量
                    2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat() // 核心：次数
                    3 -> values.maxOfOrNull { parseValue(it.rightWeight ?: "0") } ?: 0f // [新增] 力量：右边 重量
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

    // [修复逻辑] 导入周计划：改为「按日覆盖」模式
    // 逻辑：CSV 里有的天数，先清空旧数据再写入；CSV 里没有的天数，保持原样。
    fun importWeeklyRoutine(context: Context, csv: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = csv.split("\n")

                // 临时数据结构：存储解析后的待插入项
                data class PendingItem(
                    val day: Int, val name: String, val category: String, val target: String,
                    val bodyPart: String, val equipment: String, val isUni: Boolean,
                    val logType: Int
                )

                val pendingItems = mutableListOf<PendingItem>()
                val daysToOverwrite = mutableSetOf<Int>() // 记录 CSV 中涉及的天数

                // 1. 解析 CSV 阶段 (不操作数据库)
                lines.forEachIndexed { index, line ->
                    if (index == 0) return@forEachIndexed // 跳过标题
                    if (line.isBlank()) return@forEachIndexed

                    val parts = line.split(",").map { it.trim() }
                    if (parts.size >= 4) {
                        val day = parts[0].toIntOrNull() ?: 1
                        val name = parts[1]
                        val category = parts[2]
                        val target = parts[3]
                        val bodyPart = if (parts.size > 4 && parts[4].isNotBlank()) parts[4] else "part_other"
                        val equipment = if (parts.size > 5 && parts[5].isNotBlank()) parts[5] else "equip_other"
                        val isUni = if (parts.size > 6) parts[6].toBoolean() else false
                        // [新增] 解析 LogType，如果 CSV 没这列(旧版)，则根据 Category 推断
                        val inferredLogType = when(category) {
                            "CARDIO", "CORE" -> 1 // DURATION
                            else -> 0 // WEIGHT_REPS
                        }
                        val logType = if (parts.size > 7) parts[7].toIntOrNull() ?: inferredLogType else inferredLogType

                        daysToOverwrite.add(day) // 标记这一天需要被覆盖
                        pendingItems.add(PendingItem(day, name, category, target, bodyPart, equipment, isUni,logType))
                    }
                }

                if (pendingItems.isEmpty()) {
                    throw Exception("CSV is empty or invalid")
                }

                // 2. 清理旧数据阶段 (仅清理 CSV 中涉及的天数)
                daysToOverwrite.forEach { day ->
                    val oldItems = dao.getRoutineForDaySync(day)
                    oldItems.forEach { dao.deleteRoutineItem(it) }
                }

                // 3. 写入新数据阶段
                var successCount = 0
                pendingItems.forEach { item ->
                    // 查找或创建动作模板
                    var template = dao.getTemplateByName(item.name)
                    val templateId = if (template == null) {
                        val newTemp = ExerciseTemplate(
                            name = item.name,
                            category = item.category,
                            defaultTarget = item.target,
                            bodyPart = item.bodyPart,
                            equipment = item.equipment,
                            isUnilateral = item.isUni,
                            logType = item.logType
                        )
                        dao.insertTemplate(newTemp)
                    } else {
                        template.id
                    }

                    // 插入到周计划
                    dao.insertRoutineItem(WeeklyRoutineItem(
                        dayOfWeek = item.day,
                        templateId = templateId,
                        name = item.name,
                        target = item.target,
                        category = item.category,
                        bodyPart = item.bodyPart,
                        equipment = item.equipment,
                        isUnilateral = item.isUni,
                        logType = item.logType
                    ))
                    successCount++
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.import_success) + ": $successCount", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.import_error) + "\n${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // [新增] 重新加载标准动作库
    fun reloadStandardExercises(context: Context, languageCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 确定文件名
                val fileName = when (languageCode) {
                    "en" -> "exercises_en.json"
                    "ja" -> "exercises_ja.json"
                    "de" -> "exercises_de.json"
                    "es" -> "exercises_es.json"
                    else -> "default_exercises.json"
                }

                // 2. 读取新 JSON
                // 尝试打开语言特定文件，如果失败则尝试打开默认文件 (default_exercises.json)
                val jsonString = try {
                    context.assets.open(fileName).bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // 如果找不到特定语言文件，尝试加载英文版作为保底
                    try {
                        context.assets.open("default_exercises.json").bufferedReader().use { it.readText() }
                    } catch (e2: Exception) {
                        // 如果英文版也没有，尝试 exercises_en.json
                        context.assets.open("exercises_en.json").bufferedReader().use { it.readText() }
                    }
                }
                // 3. [关键步骤] 获取现有动作的 ID 映射表 (Name -> ID)
                // 这样我们可以找到同名动作的旧 ID，从而实现覆盖更新
                val listType = object : TypeToken<List<ExerciseTemplate>>() {}.type
                // [注意] 这里定义为 rawTemplates，表示原始数据
                val rawTemplates: List<ExerciseTemplate> = Gson().fromJson(jsonString, listType)

                // 4. [关键步骤] 获取现有动作的 ID 映射表 (Name -> ID)
                // 目的：如果数据库里已经有这个名字，使用旧 ID (触发更新)；否则使用 0 (触发插入)
                val existingTemplates = dao.getAllTemplatesSync()
                val nameIdMap = existingTemplates.associate { it.name to it.id }

                val finalTemplates = rawTemplates.map { newTemp ->
                    val targetId = nameIdMap[newTemp.name] ?: 0L
                    newTemp.copy(id = targetId)
                }

                // 5. 批量写入 (使用处理过的 finalTemplates)
                dao.insertTemplates(finalTemplates)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // 拼接错误信息
                    val errorMsg = context.getString(R.string.import_error) + ": ${e.message}"
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    suspend fun optimizeExerciseLibrary(): Int = 0
    // [新增 3] 获取动作的 LogType，用于图表页自动判断显示模式
    fun getLogTypeForExercise(name: String): Flow<Int> {
        return historyRecords.map { list ->
            // 找到该动作的最新一条记录，获取其 logType
            list.find { it.name == name }?.logType ?: LogType.WEIGHT_REPS.value
        }
    }
}

