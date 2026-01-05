package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(navController: NavController, viewModel: MainViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val context = LocalContext.current
    val dao = remember { com.example.myfit.data.AppDatabase.getDatabase(context).workoutDao() }
    val scheduleList by dao.getAllSchedules().collectAsState(initial = emptyList())

    var showImportDialog by remember { mutableStateOf(false) }
    var showManualRoutineDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
    ) {
        Text("高级功能", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        // 导出/导入
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.exportHistoryToCsv(context) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("导出历史", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("导入计划", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // V4.1 新增：周度方案（手动规划）入口
        Button(
            onClick = { showManualRoutineDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Default.EditCalendar, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("周度方案 (详细动作规划)", color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Text(">", color = Color.White.copy(alpha = 0.7f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 动作库入口
        Button(
            onClick = { navController.navigate("exercise_manager") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("管理动作库", color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            Text(">", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 主题
        Text("主题风格", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTheme.values().forEach { theme ->
                ThemeCircle(theme, currentTheme == theme) { viewModel.switchTheme(theme) }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // 类型设置
        Text("周计划 (类型设置)", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(scheduleList) { config ->
                ScheduleItem(config) { newType ->
                    viewModel.updateScheduleConfig(config.dayOfWeek, newType)
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)); AboutSection() }
        }
    }

    if (showImportDialog) {
        ImportDialog(onDismiss = { showImportDialog = false }) { csv ->
            viewModel.importWeeklyRoutine(csv)
            showImportDialog = false
        }
    }

    if (showManualRoutineDialog) {
        ManualRoutineDialog(viewModel, onDismiss = { showManualRoutineDialog = false })
    }
}

// V4.1 新增：手动规划弹窗
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRoutineDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var selectedDay by remember { mutableStateOf(1) } // 1=周一
    val routineItems = remember(selectedDay) { mutableStateListOf<WeeklyRoutineItem>() }
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 监听 selectedDay 变化，加载对应的 routine
    LaunchedEffect(selectedDay) {
        routineItems.clear()
        routineItems.addAll(viewModel.getRoutineForDay(selectedDay))
    }

    var showTemplateSelector by remember { mutableStateOf(false) }

    // 全屏弹窗或大型 Dialog
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("规划周度方案")
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 星期选择器
                ScrollableTabRow(
                    selectedTabIndex = selectedDay - 1,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent
                ) {
                    (1..7).forEach { day ->
                        Tab(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            text = { Text("周$day") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 当前列表
                Box(modifier = Modifier.weight(1f)) {
                    if (routineItems.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("周$selectedDay 暂无预设动作", color = Color.Gray)
                        }
                    } else {
                        LazyColumn {
                            items(routineItems) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(item.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        viewModel.removeRoutineItem(item)
                                        routineItems.remove(item)
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f))
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }

                // 3. 添加按钮
                Button(
                    onClick = { showTemplateSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加动作到周$selectedDay")
                }
            }
        },
        confirmButton = {}
    )

    // 内部动作选择器
    if (showTemplateSelector) {
        ModalBottomSheet(onDismissRequest = { showTemplateSelector = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择动作库", style = MaterialTheme.typography.headlineSmall)
                LazyColumn {
                    items(allTemplates) { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addRoutineItem(selectedDay, template)
                                    // 刷新本地列表
                                    scope.launch {
                                        routineItems.clear()
                                        routineItems.addAll(viewModel.getRoutineForDay(selectedDay))
                                    }
                                    showTemplateSelector = false
                                }
                                .padding(16.dp)
                        ) {
                            Text(template.name)
                        }
                        Divider()
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("1,坐姿推胸,力量,3组12次\n1,高位下拉,力量,3组12次\n3,跑步,有氧,30分钟") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴 CSV 内容") },
        text = {
            Column {
                Text("格式：星期(1-7),动作名,类型,目标", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ThemeCircle(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(theme.primary))
            .border(3.dp, if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun ScheduleItem(config: ScheduleConfig, onTypeChange: (DayType) -> Unit) {
    val dayName = when(config.dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; 7 -> "周日"
        else -> ""
    }
    fun nextType() = DayType.values()[(config.dayType.ordinal + 1) % DayType.values().size]

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTypeChange(nextType()) },
        colors = CardDefaults.cardColors(containerColor = Color(config.dayType.colorHex).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dayName, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(config.dayType.label, color = Color.White)
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionName = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: Exception) { "1.0" }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Info, "About", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "myFit $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Designed & Built by enersto & 哈吉米",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}