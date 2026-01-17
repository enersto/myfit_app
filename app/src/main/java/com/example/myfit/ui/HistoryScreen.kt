package com.example.myfit.ui

import android.view.HapticFeedbackConstants // [修复] 补全缺失的引用
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView // [新增引用]
// [新增] 震动反馈相关 Import
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    var selectedTabIndex by remember { mutableStateOf(1) }
    val tabTitles = listOf(
        stringResource(R.string.tab_list),
        stringResource(R.string.tab_chart)
    )

    // [新增] 获取 View 用于震动反馈
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. 内容区域：使用 weight(1f) 占据剩余空间，将 TabRow 推到底部
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> HistoryList(viewModel)
                1 -> HistoryCharts(viewModel)
            }
        }

        // 2. 底部 Tab 切换栏 (放置在 Column 底部)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        // [新增] 震动反馈 (与主屏幕一致)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        selectedTabIndex = index
                    },
                    text = {
                        Text(
                            text = title,
                            modifier = Modifier.padding(vertical = 12.dp) // 增加一点点击区域高度
                        )
                    }
                )
            }
        }
    }
}

/**
 * 历史记录列表视图
 */
@Composable
fun HistoryList(viewModel: MainViewModel) {
    val tasks by viewModel.historyRecords.collectAsState(initial = emptyList())
    val weights by viewModel.weightHistory.collectAsState(initial = emptyList())

    val historyData = remember(tasks, weights) {
        val allDates = (tasks.map { it.date } + weights.map { it.date }).distinct().sortedDescending()
        allDates.map { date ->
            Triple(date, weights.find { it.date == date }, tasks.filter { it.date == date })
        }
    }

    // 这里使用 Column 包裹是为了保持 padding 与 ScheduleScreen 一致
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 🔴 视觉修复：样式与 ScheduleScreen 的 "Advanced Features" 保持完全一致
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall, // 原为 headlineLarge
            color = MaterialTheme.colorScheme.onBackground // 原为 Primary
        )

        // 这里的 Spacer 也可以根据 ScheduleScreen 的 item 间距微调，16.dp 保持一致
        Spacer(modifier = Modifier.height(16.dp))

        if (historyData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history), color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                historyData.forEach { (date, weightRecord, dayTasks) ->
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                                if (weightRecord != null) {
                                    Surface(
                                        color = Color(0xFFFF9800).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.MonitorWeight,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFFFF9800)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${weightRecord.weight} KG",
                                                color = Color(0xFFFF9800),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (dayTasks.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history_no_train),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    } else {
                        items(dayTasks) { task ->
                            HistoryTaskCard(task)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * 图表统计视图
 */
@Composable
fun HistoryCharts(viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 🔴 视觉修复：新增标题头，确保与列表视图和设置页对齐
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // [新增] 在最上方插入热力图
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // 调用刚刚写的组件
                PixelBodyHeatmap(viewModel = viewModel, modifier = Modifier.padding(16.dp))
            }
        }

// 4) --- 模块 1: 身体状态 (合并 体重 + BMI + BMR) ---
        item {
            Text(
                stringResource(R.string.chart_title_body_status), // "Body Status"
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 1.1 体重图表
        item {
            ChartSection(title = stringResource(R.string.chart_title_weight)) { granularity ->
                val data by viewModel.getWeightChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        // 1.2 BMI 图表 (新增)
        item {
            ChartSection(title = stringResource(R.string.chart_title_bmi)) { granularity ->
                val data by viewModel.getBMIChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data, lineColor = Color(0xFFE91E63)) // 使用不同颜色区分
            }
        }

        // 1.3 BMR 图表 (新增)
        item {
            ChartSection(title = stringResource(R.string.chart_title_bmr)) { granularity ->
                val data by viewModel.getBMRChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data, lineColor = Color(0xFF9C27B0)) // 使用不同颜色区分
            }
        }

        // --- 模块 2: 有氧训练 (总时长 + 单项) ---
        item {
            Text(
                stringResource(R.string.header_cardio_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            ChartSection(
                title = stringResource(R.string.chart_title_cardio_total),
                defaultChartType = "BAR"
            ) { granularity ->
                val data by viewModel.getCardioTotalChartData(granularity).collectAsState(initial = emptyList())
                BarChart(data = data)
            }
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "CARDIO",
                title = stringResource(R.string.chart_title_cardio_single),
                defaultMode = 0 // 时长
            )
        }

        // --- 模块 3: 力量训练 (单项最大重量) ---
        item {
            Text(
                stringResource(R.string.header_strength_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "STRENGTH",
                title = stringResource(R.string.chart_title_strength_single),
                defaultMode = 1 // 重量
            )
        }

        // --- 模块 4: 核心训练 (单项总次数) ---
        item {
            Text(
                stringResource(R.string.header_core_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "CORE",
                title = stringResource(R.string.chart_title_core_single),
                defaultMode = 2 // 次数
            )
        }

        item { Spacer(modifier = Modifier.height(50.dp)) }
    }
}

// [新增/替换] 支持单边数据展示的卡片组件
@Composable
fun HistoryTaskCard(task: WorkoutTask) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 如果是单边动作，显示 "单边" 标签
                    if (task.isUnilateral) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.tag_uni),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    stringResource(R.string.btn_done),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // 2. 数据行
            if (task.sets.isNotEmpty()) {
                if (task.isUnilateral) {
                    // [核心] 单边动作：分左右两列显示
                    task.sets.forEachIndexed { index, set ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${index + 1}", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), fontSize = 12.sp)

                            // 左边
                            Row(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_side_l), fontSize = 12.sp, color = Color.Gray) // L:
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${set.weightOrDuration} x ${set.reps}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }

                            // 右边
                            Row(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_side_r), fontSize = 12.sp, color = Color.Gray) // R:
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${set.rightWeight ?: "-"} x ${set.rightReps ?: "-"}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (index < task.sets.size - 1) {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    // 普通动作：合并显示
                    val isStrength = task.category == "STRENGTH"
                    if (isStrength) {
                        val setsStr = task.sets.joinToString("  |  ") { set -> "${set.weightOrDuration} x ${set.reps}" }
                        Text(text = setsStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        task.sets.forEach { set -> Text("✅ ${set.weightOrDuration}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else {
                Text(if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}" else task.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}