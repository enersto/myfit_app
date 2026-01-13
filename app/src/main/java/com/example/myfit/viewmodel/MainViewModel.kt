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

    // [新增] 获取肌肉热力图数据
    // 返回 Map<部位Key, 热度(0.0~1.0)>
    val muscleHeatMapData: Flow<Map<String, Float>> = historyRecords.map { tasks ->
        // 1. 扁平化所有 Set，统计每个部位的总组数 (作为热度依据)
        val partCounts = mutableMapOf<String, Int>()

        tasks.forEach { task ->
            // 如果是 Cardio，可能 1 个 Task 算 1 个 "强度单位"，或者按时长算
            // 这里简单起见，按 Sets 数量统计
            val count = if (task.sets.isNotEmpty()) task.sets.size else 1
            partCounts[task.bodyPart] = partCounts.getOrDefault(task.bodyPart, 0) + count
        }

        // 2. 找出最大值，进行归一化
        val maxCount = partCounts.values.maxOrNull() ?: 1

        // 3. 转换为 0.0 ~ 1.0 的浮点数
        partCounts.mapValues { (_, count) ->
            count.toFloat() / maxCount.toFloat()
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
            // [修复] 使用 copy 更新，保持其他字段不变
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
            isUnilateral = template.isUnilateral
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
                sb.append("Date,Name,Category,Target,IsUnilateral,ActualWeight,Sets\n")

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
                    sb.append("${t.date},$safeName,${t.category},${t.target},${t.isUnilateral},${t.actualWeight},$setsStr\n")
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
                    val bodyPart: String, val equipment: String, val isUni: Boolean
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

                        daysToOverwrite.add(day) // 标记这一天需要被覆盖
                        pendingItems.add(PendingItem(day, name, category, target, bodyPart, equipment, isUni))
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
                            isUnilateral = item.isUni
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
                        isUnilateral = item.isUni
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
    suspend fun optimizeExerciseLibrary(): Int = 0
}