package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    // 主题与语言设置
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    // 日程类型逻辑
    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.REST
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.REST)

    // 核心数据流
    val todayTasks = _selectedDate.flatMapLatest { dao.getTasksForDate(it.toString()) }
    val historyRecords = dao.getHistoryRecords()
    val weightHistory = dao.getAllWeights()
    val allTemplates = dao.getAllTemplates()

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ================== 设置与基础操作 ==================

    fun switchLanguage(code: String) {
        viewModelScope.launch {
            val currentSetting = dao.getAppSettings().firstOrNull()
            val themeId = currentSetting?.themeId ?: 0
            dao.saveAppSettings(AppSetting(id = 0, themeId = themeId, languageCode = code))
        }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        val currentSetting = dao.getAppSettings().firstOrNull()
        val lang = currentSetting?.languageCode ?: "zh"
        dao.saveAppSettings(AppSetting(id = 0, themeId = theme.id, languageCode = lang))
    }

    // ================== 周计划与任务操作 ==================

    fun addRoutineItem(dayOfWeek: Int, template: ExerciseTemplate) {
        viewModelScope.launch {
            dao.insertRoutineItem(WeeklyRoutineItem(
                dayOfWeek = dayOfWeek,
                templateId = template.id,
                name = template.name,
                target = template.defaultTarget,
                category = template.category,
                bodyPart = template.bodyPart,
                equipment = template.equipment
            ))
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
                val template = dao.getTemplateById(item.templateId)
                dao.insertTask(WorkoutTask(
                    date = date.toString(),
                    templateId = item.templateId,
                    name = item.name,
                    category = item.category,
                    bodyPart = template?.bodyPart ?: item.bodyPart,
                    equipment = template?.equipment ?: item.equipment,
                    sets = listOf(WorkoutSet(1, "", "")),
                    target = item.target
                ))
            }
            Toast.makeText(getApplication(), "Routine Applied", Toast.LENGTH_SHORT).show()
        }
    }

    // ================== 导入导出与备份 (修复核心逻辑) ==================

    // 1. CSV 导出功能 (被报错说找不到的方法)
    fun exportHistoryToCsv(context: Context) {
        viewModelScope.launch {
            val records = dao.getHistoryRecordsSync()
            val sb = StringBuilder().append("Date,Name,Category,BodyPart,Equipment,Sets\n")
            records.forEach {
                val setsStr = it.sets.joinToString(" | ") { s -> "${s.weightOrDuration}x${s.reps}" }
                sb.append("${it.date},${it.name},${it.category},${it.bodyPart},${it.equipment},\"$setsStr\"\n")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "history.csv")
            }
            // 使用 Context 启动 Activity
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_csv_btn)))
        }
    }

    // 2. CSV 导入功能 (自动入库 + 去重)
    // 修改处：增加 context 参数
    fun importWeeklyRoutine(context: Context, csvContent: String) {
        viewModelScope.launch {
            // ❌ 删除旧的：val app = getApplication<Application>()
            try {
                // 1. 先执行库清理
                val deletedCount = optimizeExerciseLibrary()

                dao.clearWeeklyRoutine()
                val lines = csvContent.lines()
                var importCount = 0
                var newTemplateCount = 0

                // ✅ 修改处：使用传入的 context 获取资源
                val defaultPartKey = context.getString(R.string.val_default_body_part)
                val defaultEquipKey = context.getString(R.string.val_default_equipment)

                lines.forEach { line ->
                    val parts = line.replace("，", ",").split(",").map { it.trim() }

                    if (parts.size >= 4 && parts[0].toIntOrNull() != null) {
                        val day = parts[0].toInt()
                        val name = parts[1]
                        val catStr = parts[2].uppercase()
                        val target = parts[3]

                        val bodyPart = if (parts.size > 4 && parts[4].isNotBlank()) parts[4] else defaultPartKey
                        val equipment = if (parts.size > 5 && parts[5].isNotBlank()) parts[5] else defaultEquipKey

                        val category = when {
                            catStr.contains("CARDIO") || catStr.contains("有氧") -> "CARDIO"
                            catStr.contains("CORE") || catStr.contains("核心") -> "CORE"
                            else -> "STRENGTH"
                        }

                        // 联动动作库逻辑
                        var existingTemplate = dao.getTemplateByName(name)
                        var finalTemplateId: Long

                        if (existingTemplate != null) {
                            finalTemplateId = existingTemplate.id
                        } else {
                            val newTemplate = ExerciseTemplate(
                                name = name,
                                defaultTarget = target,
                                category = category,
                                bodyPart = bodyPart,
                                equipment = equipment,
                                isDeleted = false
                            )
                            finalTemplateId = dao.insertTemplate(newTemplate)
                            newTemplateCount++
                        }

                        dao.insertRoutineItem(WeeklyRoutineItem(
                            dayOfWeek = day,
                            templateId = finalTemplateId,
                            name = name,
                            category = category,
                            target = target,
                            bodyPart = bodyPart,
                            equipment = equipment
                        ))
                        importCount++
                    }
                }

                // 2. 构建提示信息
                val msg = StringBuilder()
                // ✅ 修改处：使用 context.getString
                msg.append(context.getString(R.string.msg_import_base, importCount))
                if (newTemplateCount > 0) {
                    msg.append(context.getString(R.string.msg_import_new_added, newTemplateCount))
                }
                if (deletedCount > 0) {
                    msg.append(context.getString(R.string.msg_import_cleaned, deletedCount))
                }

                // ✅ 修改处：使用 context 显示 Toast
                Toast.makeText(context, msg.toString(), Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                // ✅ 修改处：使用 context
                val errorMsg = context.getString(R.string.msg_import_error, e.message ?: "Unknown")
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // 3. 动作库去重辅助方法 (被报错说找不到的方法)
    private suspend fun optimizeExerciseLibrary(): Int {
        return withContext(Dispatchers.IO) {
            val allTemplates = dao.getAllTemplatesSync()
            val grouped = allTemplates.groupBy { it.name }
            val idsToDelete = mutableListOf<Long>()

            grouped.forEach { (_, templates) ->
                if (templates.size > 1) {
                    val sorted = templates.sortedByDescending { it.id }
                    for (i in 1 until sorted.size) {
                        idsToDelete.add(sorted[i].id)
                    }
                }
            }

            if (idsToDelete.isNotEmpty()) {
                dao.softDeleteTemplates(idsToDelete)
            }
            idsToDelete.size
        }
    }

    // 数据库备份
    fun backupDatabase(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    dbPath.inputStream().use { input -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_backup_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 数据库恢复
    fun restoreDatabase(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dbPath.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_restore_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_restore_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================== 动作与记录 CRUD ==================

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch { dao.softDeleteTemplate(id) }

    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        dao.insertTask(WorkoutTask(
            date = _selectedDate.value.toString(),
            templateId = t.id,
            name = t.name,
            category = t.category,
            bodyPart = t.bodyPart,
            equipment = t.equipment,
            target = t.defaultTarget,
            sets = listOf(WorkoutSet(1, "", ""))
        ))
    }

    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }

    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(day, type))
    }

    fun logWeight(w: Float) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w))
    }

    suspend fun getRoutineForDay(day: Int) = dao.getRoutineForDay(day)

    // ================== 图表统计数据逻辑 ==================

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

    private fun <T> aggregateData(
        data: List<T>,
        dateSelector: (T) -> LocalDate,
        valueSelector: (T) -> Float,
        granularity: ChartGranularity
    ): List<ChartDataPoint> {
        val grouped = when (granularity) {
            ChartGranularity.DAILY -> data.groupBy { dateSelector(it) }
            ChartGranularity.MONTHLY -> data.groupBy { dateSelector(it).withDayOfMonth(1) }
        }

        return grouped.map { (date, items) ->
            val value = items.map { valueSelector(it) }.average().toFloat()
            val label = if (granularity == ChartGranularity.DAILY)
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            else
                date.format(DateTimeFormatter.ofPattern("yy/MM"))
            ChartDataPoint(date, value, label)
        }.sortedBy { it.date }
    }

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return weightHistory.map { records ->
            val raw = records.map { Pair(LocalDate.parse(it.date), it.weight) }
            val dailyMap = raw.groupBy { it.first }.mapValues { it.value.last().second }
            val dailyList = dailyMap.map { ChartDataPoint(it.key, it.value, "") }
            aggregateData(dailyList, { it.date }, { it.value }, granularity)
        }
    }

    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return dao.getHistoryRecords().map { tasks ->
            val cardioTasks = tasks.filter { it.category == "CARDIO" }
            val dailySums = cardioTasks.groupBy { LocalDate.parse(it.date) }
                .mapValues { (_, dayTasks) ->
                    dayTasks.sumOf { task ->
                        if (task.sets.isNotEmpty()) {
                            task.sets.sumOf { parseDuration(it.weightOrDuration).toDouble() }
                        } else {
                            parseDuration(task.target).toDouble()
                        }
                    }.toFloat()
                }
            val dailyData = dailySums.map { ChartDataPoint(it.key, it.value, "") }
            aggregateData(dailyData, { it.date }, { it.value }, granularity)
        }
    }

    fun getExerciseNamesByCategory(category: String): Flow<List<String>> {
        return dao.getHistoryRecords().map { tasks ->
            tasks.filter { it.category == category }
                .map { it.name }
                .distinct()
                .sorted()
        }
    }

    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return dao.getHistoryRecords().map { tasks ->
            val targetTasks = tasks.filter { it.name == name }
            val dailyValues = targetTasks.groupBy { LocalDate.parse(it.date) }
                .mapValues { (_, dayTasks) ->
                    val values = dayTasks.flatMap { task ->
                        if (task.sets.isNotEmpty()) task.sets else listOf(WorkoutSet(1, task.actualWeight.ifEmpty { task.target }, task.target))
                    }
                    when (mode) {
                        0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat()
                        1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f
                        2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat()
                        else -> 0f
                    }
                }
            val dailyData = dailyValues.map { ChartDataPoint(it.key, it.value, "") }
            aggregateData(dailyData, { it.date }, { it.value }, granularity)
        }
    }
}