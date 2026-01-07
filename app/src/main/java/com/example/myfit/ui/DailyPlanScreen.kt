package com.example.myfit.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random
import androidx.compose.foundation.lazy.items

// 🔴 关键修复：将 OptIn 注解放在整个文件入口函数上，一劳永逸
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) {
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList<WorkoutTask>())
    val showWeightAlert by viewModel.showWeightAlert.collectAsState()
    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExplosion by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                HeaderSection(date, dayType, progress, themeColor, showWeightAlert) { showWeightDialog = true }
                Spacer(modifier = Modifier.height(20.dp))

                if (tasks.isEmpty()) {
                    EmptyState(dayType) { viewModel.applyWeeklyRoutineToToday() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            SwipeToDeleteContainer(item = task, onDelete = { viewModel.removeTask(task) }) {
                                AdvancedTaskItem(task, themeColor, viewModel) { showExplosion = true }
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
    }
}

@Composable
fun AdvancedTaskItem(task: WorkoutTask, themeColor: Color, viewModel: MainViewModel, onComplete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isCompleted = task.isCompleted
    val cardBgColor = if (isCompleted) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.surface
    val contentAlpha = if (isCompleted) 0.5f else 1f

    val bodyPartRes = getBodyPartResId(task.bodyPart)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else task.bodyPart

    val equipRes = getEquipmentResId(task.equipment)
    val equipLabel = if (equipRes != 0) stringResource(equipRes) else task.equipment

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
                        Text(text = "$bodyPartLabel  |  $equipLabel", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

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
fun HeaderSection(date: LocalDate, dayType: DayType, progress: Float, color: Color, showWeightAlert: Boolean, onWeightClick: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${date.monthValue} / ${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            if (showWeightAlert) Button(onClick = onWeightClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.log_weight), fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(dayType.labelResId), color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
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
    // 1. 新增：分类状态
    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 2. 新增：顶部管理入口
            Button(
                onClick = { onDismiss(); navController.navigate("exercise_manager") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.new_manage_lib))
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 3. 新增：分类 Tab
            TabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                categories.forEach { category ->
                    // 简单的映射 helper (由于不在 ExerciseManagerScreen，这里简单内联处理或使用硬编码Key对应资源)
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

            // 4. 修改：带过滤的列表
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
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun WeightDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var weightInput by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = { weightInput.toFloatOrNull()?.let { viewModel.logWeight(it) }; onDismiss() }) { Text(stringResource(R.string.btn_save)) } }, title = { Text(stringResource(R.string.dialog_weight_title)) }, text = { OutlinedTextField(value = weightInput, onValueChange = { weightInput = it }, label = { Text("KG") }) })
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

// 🔴 关键修复：直接在此函数上也加上 OptIn，双保险
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