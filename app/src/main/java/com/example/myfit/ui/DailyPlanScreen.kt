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

// [修复] 删除了重复的 getBodyPartResId 和 getEquipmentResId 定义
// 它们现在直接引用 ExerciseManagerScreen.kt 中的公共定义

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) {
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList<WorkoutTask>())
    val showWeightAlert by viewModel.showWeightAlert.collectAsState()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()

    // [新增] 监听是否已展示过引导
    val hasShownGuide by viewModel.hasShownLockScreenGuide.collectAsState()

    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExplosion by remember { mutableStateOf(false) }

    // [新增] 控制锁屏引导弹窗的状态
    var showLockScreenSetupDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // [修改] 权限回调：如果同意了通知权限，且从未展示过引导，则弹窗
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
                    FloatingActionButton(onClick = { showAddSheet = true }, containerColor = themeColor) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            // [注意] 这里的 padding(16.dp) 确保了与 HistoryScreen 和 ScheduleScreen 的根 padding 一致
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

        if (showExplosion) ExplosionEffect { showExplosion = false }
        if (showAddSheet) AddExerciseSheet(viewModel, navController) { showAddSheet = false }
        if (showWeightDialog) WeightDialog(viewModel) { showWeightDialog = false }

        // [新增] 锁屏通知引导弹窗 (完全使用资源字符串)
        if (showLockScreenSetupDialog) {
            AlertDialog(
                onDismissRequest = {
                    // 点击外部取消时，也视为“已读”，不再打扰
                    viewModel.markLockScreenGuideShown()
                    showLockScreenSetupDialog = false
                },
                title = { Text(stringResource(R.string.dialog_lock_screen_title)) },
                text = { Text(stringResource(R.string.dialog_lock_screen_content)) },
                confirmButton = {
                    Button(onClick = {
                        // 点击去设置，标记为已读
                        viewModel.markLockScreenGuideShown()
                        NotificationHelper.openNotificationSettings(context)
                        showLockScreenSetupDialog = false
                    }) {
                        Text(stringResource(R.string.btn_go_to_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        // 点击稍后，标记为已读（不再打扰）
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

// ... AdvancedTaskItem 及后续代码保持不变 ...
@Composable
fun AdvancedTaskItem(
    task: WorkoutTask,
    themeColor: Color,
    viewModel: MainViewModel,
    timerState: MainViewModel.TimerState,
    onComplete: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isCompleted = task.isCompleted
    val cardBgColor = if (isCompleted) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.surface
    val contentAlpha = if (isCompleted) 0.5f else 1f

    // 引用公共资源获取函数
    val bodyPartRes = getBodyPartResId(task.bodyPart)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else task.bodyPart
    val equipRes = getEquipmentResId(task.equipment)
    val equipLabel = if (equipRes != 0) stringResource(equipRes) else task.equipment
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text(text = "$bodyPartLabel | $equipLabel", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                PillCheckButton(isCompleted = isCompleted, color = themeColor, onClick = {
                    val newState = !task.isCompleted
                    viewModel.updateTask(task.copy(isCompleted = newState))
                    if (newState) onComplete()
                })
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    val isStrength = task.category.uppercase().trim() == "STRENGTH"

                    if (isStrength) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.header_set_no), modifier = Modifier.weight(0.5f), fontSize = 12.sp, color = Color.Gray)
                            Text(stringResource(R.string.header_weight_time), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                            Text(stringResource(R.string.header_reps), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        task.sets.forEachIndexed { index, set ->
                            SetRow(index, set, themeColor) { updatedSet ->
                                val newSets = task.sets.toMutableList()
                                newSets[index] = updatedSet
                                viewModel.updateTask(task.copy(sets = newSets))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } else {
                        task.sets.forEachIndexed { index, set ->
                            TimerSetRow(
                                index = index,
                                set = set,
                                defaultDuration = parseDefaultDuration(task.target),
                                taskId = task.id,
                                timerState = timerState,
                                themeColor = themeColor,
                                onStart = { min ->
                                    onRequestPermission()
                                    viewModel.startTimer(context, task.id, index, min)
                                },
                                onPause = { viewModel.pauseTimer(context) },
                                onStop = { viewModel.stopTimer(context) },
                                onRemove = {
                                    val newSets = task.sets.toMutableList().apply { removeAt(index) }
                                    viewModel.updateTask(task.copy(sets = newSets))
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.2f))
                        }
                    }

                    TextButton(
                        onClick = {
                            val lastSet = task.sets.lastOrNull()
                            val newSet = WorkoutSet(
                                setNumber = task.sets.size + 1,
                                weightOrDuration = lastSet?.weightOrDuration ?: "",
                                reps = lastSet?.reps ?: ""
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
fun SetRow(index: Int, set: WorkoutSet, color: Color, onUpdate: (WorkoutSet) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${set.setNumber}", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = Color.Gray)
        Surface(modifier = Modifier.weight(1f).padding(end = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
            BasicTextField(
                value = set.weightOrDuration,
                onValueChange = { onUpdate(set.copy(weightOrDuration = it)) },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = SolidColor(color),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
            BasicTextField(
                value = set.reps,
                onValueChange = { onUpdate(set.copy(reps = it)) },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = SolidColor(color),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun TimerSetRow(
    index: Int,
    set: WorkoutSet,
    defaultDuration: String,
    taskId: Long,
    timerState: MainViewModel.TimerState,
    themeColor: Color,
    onStart: (Int) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit
) {
    val isActive = timerState.taskId == taskId && timerState.setIndex == index
    var inputMinutes by remember { mutableStateOf(defaultDuration) }

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
                val s = timerState.remainingSeconds
                val timeStr = String.format("%02d:%02d", s / 60, s % 60)
                Text(text = timeStr, style = MaterialTheme.typography.headlineMedium, color = themeColor, fontWeight = FontWeight.Bold)
            } else if (set.weightOrDuration.isNotBlank() && (set.weightOrDuration.contains("min") || set.reps == "Done")) {
                Text(text = "✅ ${set.weightOrDuration}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(80.dp)) {
                        BasicTextField(
                            value = inputMinutes,
                            onValueChange = { if (it.all { char -> char.isDigit() }) inputMinutes = it },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            cursorBrush = SolidColor(themeColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.label_min), fontSize = 14.sp, color = Color.Gray)
                }
            }
        }

        Row {
            if (isActive) {
                if (timerState.isRunning) {
                    IconButton(onClick = onPause) { Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.timer_pause), tint = Color.Gray) }
                } else {
                    IconButton(onClick = { onStart(timerState.totalSeconds / 60) }) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.timer_resume), tint = themeColor) }
                }
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.timer_stop), tint = Color.Red) }
            } else if (set.weightOrDuration.isBlank()) {
                IconButton(onClick = { val minutes = inputMinutes.toIntOrNull() ?: 30; onStart(minutes) }) {
                    Icon(Icons.Default.PlayCircle, contentDescription = stringResource(R.string.timer_start), tint = themeColor, modifier = Modifier.size(32.dp))
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
        // 第一行：日期 (独占一行，防止挤压)
        val dateText = remember(date) {
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 5) 第二行：记录按钮 (单独一行，位于日期和类型之间)
        if (showWeightAlert) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onWeightClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.height(36.dp).align(Alignment.Start), // 左对齐
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.log_weight), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp)) // 拉开间距

        // 第三行：类型
        Text(stringResource(dayType.labelResId), color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        // 进度条
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

    // 逻辑判定：是否需要显示详细信息
    // 规则：第一次记录(身高/年龄为0) 或者 年龄 < 22 岁时，需要确认身高年龄
    val needFullInfo = remember(profile) {
        profile.height == 0f || profile.age == 0 || profile.age < 22
    }

    var weightInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var heightInput by remember { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var selectedGender by remember { mutableStateOf(profile.gender) } // 0 male, 1 female

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (needFullInfo) R.string.dialog_profile_title else R.string.dialog_weight_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. 体重 (必填)
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text(stringResource(R.string.label_weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                // 2. 详细信息 (条件显示)
                if (needFullInfo) {
                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text(stringResource(R.string.hint_input_age)) }, // "Age"
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text(stringResource(R.string.hint_input_height)) }, // "Height"
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
                    // 如果显示了详细信息，则解析并更新；否则传 null (保持原值)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = Color.Red
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        content = { content() }
    )
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