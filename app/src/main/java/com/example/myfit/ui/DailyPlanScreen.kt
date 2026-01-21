package com.example.myfit.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.util.NotificationHelper
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

// 动画核心库
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
// 手势处理库
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
// UI 基础库
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

import com.example.myfit.viewmodel.TimerPhase

import com.example.myfit.model.LogType           // [新增]

// [新增] 用于图片显示
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.ui.draw.clip // [检查] 确保有这一行

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) {
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList<WorkoutTask>())
    // [新增] 获取所有动作模板，用于详情页联动
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())

    val showWeightAlert by viewModel.showWeightAlert.collectAsState()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()

    val hasShownGuide by viewModel.hasShownLockScreenGuide.collectAsState()

    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExplosion by remember { mutableStateOf(false) }

    var showLockScreenSetupDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                if (!hasShownGuide) {
                    showLockScreenSetupDialog = true
                }
            } else {
                Toast.makeText(context, "Notification permission required", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (dayType != DayType.REST) {
                    FloatingActionButton(onClick = { navController.navigate("exercise_selector") }, containerColor = themeColor) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {

                HeaderSection(date, dayType, progress, themeColor, showWeightAlert) { showWeightDialog = true }

                Spacer(modifier = Modifier.height(16.dp))

                if (tasks.isEmpty()) {
                    EmptyState(dayType) { viewModel.applyWeeklyRoutineToToday() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            SwipeToDeleteContainer<WorkoutTask>(item = task, onDelete = { viewModel.removeTask(task) }) {
                                AdvancedTaskItem(
                                    task = task,
                                    allTemplates = allTemplates,
                                    themeColor = themeColor,
                                    viewModel = viewModel,
                                    timerState = timerState,
                                    onComplete = { showExplosion = true },
                                    onRequestPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // [新增] 全屏倒计时遮罩
        if (timerState.isRunning && timerState.showBigAlert) {
            CountdownOverlay(timerState)
        }

        if (showExplosion) ExplosionEffect { showExplosion = false }
        if (showAddSheet) AddExerciseSheet(viewModel, navController) { showAddSheet = false }
        if (showWeightDialog) WeightDialog(viewModel) { showWeightDialog = false }

        if (showLockScreenSetupDialog) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.markLockScreenGuideShown()
                    showLockScreenSetupDialog = false
                },
                title = { Text(stringResource(R.string.dialog_lock_screen_title)) },
                text = { Text(stringResource(R.string.dialog_lock_screen_content)) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.markLockScreenGuideShown()
                        NotificationHelper.openNotificationSettings(context)
                        showLockScreenSetupDialog = false
                    }) {
                        Text(stringResource(R.string.btn_go_to_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.markLockScreenGuideShown()
                        showLockScreenSetupDialog = false
                    }) {
                        Text(stringResource(R.string.btn_later))
                    }
                }
            )
        }
    }
}

@Composable
fun AdvancedTaskItem(
    task: WorkoutTask,
    allTemplates: List<ExerciseTemplate>,
    themeColor: Color,
    viewModel: MainViewModel,
    timerState: MainViewModel.TimerState,
    onComplete: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // [新增] 详情弹窗控制
    var showDetailInfo by remember { mutableStateOf(false) }
    val isCompleted = task.isCompleted
    val cardBgColor = if (isCompleted) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.surface
    val contentAlpha = if (isCompleted) 0.5f else 1f

    val bodyPartRes = getBodyPartResId(task.bodyPart)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else task.bodyPart
    val equipRes = getEquipmentResId(task.equipment)
    val equipLabel = if (equipRes != 0) stringResource(equipRes) else task.equipment
    val context = LocalContext.current

    // [新增] 如果需要显示详情，这里临时构造一个 Template 对象传给 Dialog
    // [关键修改] 详情展示逻辑：优先使用实时模板数据
    if (showDetailInfo) {
        // 尝试从实时库中找到对应的 Template
        val liveTemplate = allTemplates.find { it.id == task.templateId }

        // 如果找到了实时模板，就用实时的（含最新图片和说明）；如果找不到（被删了），就用 Task 快照兜底
        val displayTemplate = liveTemplate ?: ExerciseTemplate(
            id = task.templateId,
            name = task.name,
            category = task.category,
            bodyPart = task.bodyPart,
            equipment = task.equipment,
            isUnilateral = task.isUnilateral,
            logType = task.logType,
            instruction = "", // 快照没有说明，所以如果模板被删，这里就是空
            imageUri = task.imageUri,
            defaultTarget = task.target
        )

        ExerciseDetailDialog(template = displayTemplate, onDismiss = { showDetailInfo = false }, onEdit = null)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- 顶部行：名称、标签、打卡按钮 (保持原有逻辑) ---
            Row(verticalAlignment = Alignment.CenterVertically) {

                // 这样你在管理页改了图，打卡页列表不用展开也能看到新图
                val liveImageUri = allTemplates.find { it.id == task.templateId }?.imageUri ?: task.imageUri

                // [新增] 图片展示 (如果有) - 逻辑类似 ExerciseMinimalCard
                if (!task.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = if (task.imageUri!!.startsWith("/")) File(task.imageUri!!) else task.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.LightGray)
                            // [交互] 如果 bar 未展开，图片在最左侧；展开后点击图片也可以看大图(可选)
                            .clickable(enabled = expanded) { showDetailInfo = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.name,
                            color = if (isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else if (expanded) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                            modifier = Modifier.clickable(enabled = expanded) {
                                // [交互] 只有展开时，点击标题才弹出详情
                                showDetailInfo = true
                            }
                        )
                        // [新增] 展开时的提示小图标或文字
                        if (expanded) {
                            Text(
                                text = stringResource(R.string.hint_tap_title_for_detail),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.isUnilateral) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.label_unilateral_mode),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "$bodyPartLabel | $equipLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                PillCheckButton(isCompleted = isCompleted, color = themeColor, onClick = {
                    val newState = !task.isCompleted
                    var updatedTask = task.copy(isCompleted = newState)

                    // 自动填充逻辑：如果有氧/核心任务打卡，且未填数据，自动填入目标值
                    if (newState && (task.category == "CARDIO" || task.category == "CORE")) {
                        val filledSets = task.sets.map { set ->
                            if (set.weightOrDuration.isBlank()) {
                                set.copy(
                                    weightOrDuration = task.target.replace(" ", ""),
                                    reps = "Done"
                                )
                            } else {
                                set
                            }
                        }
                        updatedTask = updatedTask.copy(sets = filledSets)
                    }

                    viewModel.updateTask(updatedTask)
                    if (newState) onComplete()
                })
            }

            // --- 展开区域：组列表 ---
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // [修改] 使用 LogType 判断显示模式
                    val isTimer = task.logType == LogType.DURATION.value

                    if (!isTimer) {
                        // --- 力量/计次 模式 ---
                        val isRepsOnly = task.logType == LogType.REPS_ONLY.value

                        // 表头行 (根据 isRepsOnly 动态隐藏 "重量/时间" 列)
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.header_set_no),
                                modifier = Modifier.weight(0.5f),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (!isRepsOnly) {
                                Text(
                                    text = stringResource(R.string.header_weight_time),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = stringResource(R.string.header_reps),
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        task.sets.forEachIndexed { index, set ->
                            SetRow(
                                index = index,
                                set = set,
                                color = themeColor,
                                isUnilateral = task.isUnilateral,
                                isRepsOnly = isRepsOnly, // [新增参数] 传递给 SetRow
                                onUpdate = { updatedSet ->
                                    val newSets = task.sets.toMutableList()
                                    newSets[index] = updatedSet
                                    viewModel.updateTask(task.copy(sets = newSets))
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } else {
                        // --- 计时模式 ---
                        // [新增] 核心运动显示秒数输入框
                        val showSeconds = task.category == "CORE"

                        task.sets.forEachIndexed { index, set ->
                            TimerSetRow(
                                index = index,
                                set = set,
                                defaultDuration = parseDefaultDuration(task.target),
                                taskId = task.id,
                                timerState = timerState,
                                themeColor = themeColor,
                                showSeconds = showSeconds, // [新增参数] 传递给 TimerSetRow
                                onStart = { minutesFloat ->
                                    onRequestPermission()
                                    // 注意：ViewModel目前只接受Int分钟，暂时转换，后续建议升级ViewModel支持Float
                                    viewModel.startTimer(context, task.id, index, minutesFloat)
                                },
                                onPause = { viewModel.pauseTimer(context) },
                                onStop = { viewModel.stopTimer(context) },
                                onRemove = {
                                    val newSets = task.sets.toMutableList().apply { removeAt(index) }
                                    viewModel.updateTask(task.copy(sets = newSets))
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color.LightGray.copy(alpha = 0.2f)
                            )
                        }
                    }

                    // 添加组按钮 (保持不变)
                    TextButton(
                        onClick = {
                            val lastSet = task.sets.lastOrNull()
                            // 创建新组，继承上一组的数据
                            val newSet = WorkoutSet(
                                setNumber = task.sets.size + 1,
                                weightOrDuration = lastSet?.weightOrDuration ?: "",
                                reps = lastSet?.reps ?: "",
                                rightWeight = lastSet?.rightWeight,
                                rightReps = lastSet?.rightReps
                            )
                            viewModel.updateTask(task.copy(sets = task.sets + newSet))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("+ ${stringResource(R.string.btn_add_set)}")
                    }
                }
            }
        }
    }
}

private fun parseDefaultDuration(target: String): String {
    val regex = Regex("\\d+")
    val match = regex.find(target)
    return match?.value ?: "30"
}

@Composable
fun SetRow(
    index: Int,
    set: WorkoutSet,
    color: Color,
    isUnilateral: Boolean = false,
    isRepsOnly: Boolean = false, // [新增参数]
    onUpdate: (WorkoutSet) -> Unit
) {
    var weightInput by remember(set.weightOrDuration) { mutableStateOf(set.weightOrDuration) }
    var repsInput by remember(set.reps) { mutableStateOf(set.reps) }

    var rightWeightInput by remember(set.rightWeight) { mutableStateOf(set.rightWeight ?: "") }
    var rightRepsInput by remember(set.rightReps) { mutableStateOf(set.rightReps ?: "") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${set.setNumber}", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = Color.Gray)

        if (isUnilateral) {
            Column(modifier = Modifier.weight(2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_side_left),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.defaultMinSize(minWidth = 20.dp).padding(end = 4.dp)
                    )
                    if (!isRepsOnly) {
                        InputBox(weightInput, color, Modifier.weight(1f)) {
                            weightInput = it
                            onUpdate(set.copy(weightOrDuration = it))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    InputBox(repsInput, color, Modifier.weight(1f)) {
                        repsInput = it
                        onUpdate(set.copy(reps = it))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_side_right),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.defaultMinSize(minWidth = 20.dp).padding(end = 4.dp)
                    )
                    if (!isRepsOnly) {
                        InputBox(rightWeightInput, color, Modifier.weight(1f)) {
                            rightWeightInput = it
                            onUpdate(set.copy(rightWeight = it))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    InputBox(rightRepsInput, color, Modifier.weight(1f)) {
                        rightRepsInput = it
                        onUpdate(set.copy(rightReps = it))
                    }
                }
            }
        } else {
            if (!isRepsOnly) {
                InputBox(weightInput, color, Modifier.weight(1f).padding(end = 8.dp)) {
                weightInput = it
                onUpdate(set.copy(weightOrDuration = it))
            }
            }
            InputBox(repsInput, color, Modifier.weight(1f)) {
                repsInput = it
                onUpdate(set.copy(reps = it))
            }
        }
    }
}

@Composable
fun InputBox(
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            singleLine = true,
            cursorBrush = SolidColor(color),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

// ... TimerSetRow, HeaderSection, EmptyState, AddExerciseSheet, WeightDialog, PillCheckButton, SwipeToDeleteContainer, ExplosionEffect 保持不变 ...
// 务必确保以下这些函数都存在于文件中，避免编译错误

@Composable
fun TimerSetRow(
    index: Int,
    set: WorkoutSet,
    defaultDuration: String,
    taskId: Long,
    timerState: MainViewModel.TimerState,
    themeColor: Color,
    showSeconds: Boolean = false, // [新增] 控制是否显示秒
    onStart: (Float) -> Unit,     // [修改] 参数类型改为 Float
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit
) {
    val isActive = timerState.taskId == taskId && timerState.setIndex == index

    // 输入状态
    var inputMinutes by remember { mutableStateOf(defaultDuration) }
    var inputSeconds by remember { mutableStateOf(if (showSeconds) "30" else "0") } // 秒数默认值

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(30.dp),
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )

        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (isActive) {
                // --- 倒计时显示逻辑 ---
                val s = timerState.remainingSeconds
                val timeStr = String.format("%02d:%02d", s / 60, s % 60)
                val isPrep = timerState.phase == TimerPhase.PREP
                val displayColor = if (isPrep) Color(0xFFFF9800) else themeColor

                Column(horizontalAlignment = Alignment.Start) {
                    if (isPrep) {
                        Text("PREP", style = MaterialTheme.typography.labelSmall, color = displayColor, fontWeight = FontWeight.Bold)
                    }
                    Text(timeStr, style = MaterialTheme.typography.headlineMedium, color = displayColor, fontWeight = FontWeight.Bold)
                }
            } else if (set.weightOrDuration.isNotBlank() && (set.weightOrDuration.contains("min") || set.reps == "Done")) {
                Text(text = "✅ ${set.weightOrDuration}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            } else {
                // --- 输入框逻辑 ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 分钟输入
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(50.dp)) {
                        BasicTextField(
                            value = inputMinutes,
                            onValueChange = { if (it.all { char -> char.isDigit() }) inputMinutes = it },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(8.dp),
                            cursorBrush = SolidColor(themeColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.label_min), fontSize = 14.sp, color = Color.Gray)

                    // [新增] 秒数输入 (仅当 showSeconds=true 时显示)
                    if (showSeconds) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(50.dp)) {
                            BasicTextField(
                                value = inputSeconds,
                                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) inputSeconds = it },
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.padding(8.dp),
                                cursorBrush = SolidColor(themeColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.label_sec), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // 按钮区域
        Row {
            if (isActive) {
                if (timerState.isRunning) {
                    IconButton(onClick = onPause) { Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.Gray) }
                } else {
                    // [修复] 恢复计时：使用 60f 强制转换为 Float，解决编译错误
                    IconButton(onClick = { onStart(timerState.totalSeconds / 60f) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = themeColor)
                    }
                }
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red) }
            } else if (set.weightOrDuration.isBlank()) {
                // [修复] 开始计时：计算 Float 类型的总分钟数，解决编译错误
                IconButton(onClick = {
                    val m = inputMinutes.toIntOrNull() ?: 0
                    val s = inputSeconds.toIntOrNull() ?: 0
                    val totalMin = m + (s / 60f) // 这里结果是 Float
                    onStart(totalMin)
                }) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Start", tint = themeColor, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.LightGray) }
            } else {
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.LightGray) }
            }
        }
    }
}

@Composable
fun HeaderSection(date: LocalDate, dayType: DayType, progress: Float, color: Color, showWeightAlert: Boolean, onWeightClick: () -> Unit) {
    Column {
        val dateText = remember(date) {
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (showWeightAlert) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onWeightClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.height(36.dp).align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.log_weight), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(dayType.labelResId), color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun EmptyState(dayType: DayType, onApplyRoutine: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (dayType == DayType.REST) Text(stringResource(R.string.type_rest), color = Color.Gray)
            else { Text(stringResource(R.string.no_plan)); Button(onClick = onApplyRoutine) { Text(stringResource(R.string.apply_routine)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(viewModel: MainViewModel, navController: NavController, onDismiss: () -> Unit) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onDismiss(); navController.navigate("exercise_manager") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.new_manage_lib))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                categories.forEach { category ->
                    val labelRes = when(category) {
                        "STRENGTH" -> R.string.category_strength
                        "CARDIO" -> R.string.category_cardio
                        "CORE" -> R.string.category_core
                        else -> R.string.category_strength
                    }
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val filtered = templates.filter { it.category == selectedCategory }

                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.chart_no_data), color = Color.Gray)
                        }
                    }
                } else {
                    items(filtered) { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.addTaskFromTemplate(t); onDismiss() }
                                .padding(16.dp, 12.dp)
                        ) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun WeightDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()

    val needFullInfo = remember(profile) {
        profile.height == 0f || profile.age == 0 || profile.age < 22
    }

    var weightInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var heightInput by remember { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var selectedGender by remember { mutableStateOf(profile.gender) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (needFullInfo) R.string.dialog_profile_title else R.string.dialog_weight_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text(stringResource(R.string.label_weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                if (needFullInfo) {
                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text(stringResource(R.string.hint_input_age)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text(stringResource(R.string.hint_input_height)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_gender) + ": ", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedGender == 0,
                            onClick = { selectedGender = 0 },
                            label = { Text(stringResource(R.string.gender_male)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedGender == 1,
                            onClick = { selectedGender = 1 },
                            label = { Text(stringResource(R.string.gender_female)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weightInput.toFloatOrNull()
                if (w != null) {
                    val a = if (needFullInfo) ageInput.toIntOrNull() else null
                    val h = if (needFullInfo) heightInput.toFloatOrNull() else null
                    val g = if (needFullInfo) selectedGender else null

                    viewModel.logWeightAndProfile(w, a, h, g)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun PillCheckButton(isCompleted: Boolean, color: Color, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isCompleted) 0.95f else 1f)
    Surface(onClick = onClick, modifier = Modifier.height(36.dp).scale(scale), shape = RoundedCornerShape(50), color = if (isCompleted) Color.LightGray else color) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(if (isCompleted) stringResource(R.string.btn_done) else stringResource(R.string.btn_check), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val offsetX = remember { Animatable(0f) }

    val revealThresholdPx = with(density) { 80.dp.toPx() }
    val deleteThresholdPx = screenWidthPx * 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (offsetX.value < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .clickable {
                        onDelete(item)
                    }
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val target = (offsetX.value + delta).coerceAtMost(0f)
                            offsetX.snapTo(target)
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val currentX = abs(offsetX.value)

                        when {
                            currentX >= deleteThresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch {
                                    offsetX.animateTo(-screenWidthPx, tween(300))
                                    onDelete(item)
                                    offsetX.snapTo(0f)
                                }
                            }
                            currentX >= revealThresholdPx / 2 -> {
                                launch {
                                    offsetX.animateTo(-revealThresholdPx, tween(300))
                                }
                            }
                            else -> {
                                launch {
                                    offsetX.animateTo(0f, tween(300))
                                }
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
fun ExplosionEffect(onDismiss: () -> Unit) {
    val particles = remember { List(20) { Particle() } }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1000); visible = false; onDismiss() }
    if (visible) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("🎉", fontSize = 100.sp)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                particles.forEach { p ->
                    val x = center.x + p.radius * cos(p.angle)
                    val y = center.y + p.radius * sin(p.angle)
                    drawCircle(color = p.color, radius = 8f, center = Offset(x.toFloat(), y.toFloat()))
                    p.update()
                }
            }
        }
    }
}
class Particle {
    var angle = Random.nextDouble(0.0, 2 * PI)
    var radius = 0.0
    var speed = Random.nextDouble(10.0, 30.0)
    val color = listOf(Color.Red, Color.Yellow, Color.Blue, Color.Green).random()
    fun update() { radius += speed }
}

// [新增] 组件实现
@Composable
fun CountdownOverlay(timerState: MainViewModel.TimerState) {
    val isPrep = timerState.phase == TimerPhase.PREP
    val color = if (isPrep) Color(0xFFFF9800) else Color(0xFFF44336) // 橙色准备，红色结束
    val title = if (isPrep) stringResource(R.string.timer_overlay_prep) else stringResource(R.string.timer_overlay_finish)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // 拦截点击
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 巨大的数字
            Text(
                text = "${timerState.remainingSeconds}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
                color = color,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}