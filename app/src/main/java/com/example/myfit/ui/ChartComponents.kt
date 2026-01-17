package com.example.myfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate

import com.example.myfit.model.LogType           // [新增]

enum class ChartGranularity { DAILY, MONTHLY }
data class ChartDataPoint(val date: LocalDate, val value: Float, val label: String)

@Composable
fun ChartSection(
    title: String,
    defaultChartType: String = "LINE",
    content: @Composable (ChartGranularity) -> Unit
) {
    var granularity by remember { mutableStateOf(ChartGranularity.DAILY) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    GranularityButton(
                        stringResource(R.string.chart_granularity_day),
                        granularity == ChartGranularity.DAILY
                    ) { granularity = ChartGranularity.DAILY }

                    GranularityButton(
                        stringResource(R.string.chart_granularity_month),
                        granularity == ChartGranularity.MONTHLY
                    ) { granularity = ChartGranularity.MONTHLY }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                content(granularity)
            }
        }
    }
}

@Composable
fun GranularityButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (isSelected) Color.White else Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SingleExerciseSection(
    viewModel: MainViewModel,
    category: String,
    title: String,
    // [注意] 这里的 mode 参数保留用于兼容，但在内部逻辑中，我们会优先使用动作自身的 LogType
    defaultMode: Int = 1
) {
    var selectedExercise by remember { mutableStateOf("") }
    // 0=左/默认, 1=右
    var selectedSide by remember { mutableStateOf(0) }

    val exercises by viewModel.getExerciseNamesByCategory(category).collectAsStateWithLifecycle(initialValue = emptyList())

    // [关键恢复] 获取历史记录以判断属性
    val history by viewModel.historyRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    // [逻辑] 判断当前选中的动作是否为单边动作
    // 只要历史记录里有一条该名称的记录标记为 isUnilateral=true，就视为单边动作
    val isUnilateral by remember(selectedExercise, history) {
        derivedStateOf {
            if (selectedExercise.isEmpty()) false
            else history.any { it.name == selectedExercise && it.isUnilateral }
        }
    }

    // 2. [关键新增] 获取动作的 LogType
    val logType by remember(selectedExercise) {
        viewModel.getLogTypeForExercise(selectedExercise)
    }.collectAsStateWithLifecycle(initialValue = LogType.WEIGHT_REPS.value)

    var expanded by remember { mutableStateOf(false) }

    if (exercises.isNotEmpty()) {
        if (selectedExercise.isEmpty()) selectedExercise = exercises.first()

        Column {
            // 1. 下拉选择器
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(selectedExercise, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    exercises.forEach { name ->
                        val isSelected = (name == selectedExercise)
                        val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedExercise = name
                                expanded = false
                                selectedSide = 0 // 切换动作时重置为左边
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = textColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 左右切换开关 (恢复判断逻辑：仅当 category 为 STRENGTH 且 isUnilateral 为 true 时显示)
            if (category == "STRENGTH" && isUnilateral) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    FilterChip(
                        selected = selectedSide == 0,
                        onClick = { selectedSide = 0 },
                        label = { Text(stringResource(R.string.label_side_left)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = selectedSide == 1,
                        onClick = { selectedSide = 1 },
                        label = { Text(stringResource(R.string.label_side_right)) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. 计算 Mode (如果选了右边，请求 mode=3)
            // --- 动态计算 effectiveMode ---
            val effectiveMode = when (logType) {
                LogType.DURATION.value -> 0   // 计时 -> 显示总时长
                LogType.REPS_ONLY.value -> 2  // 自重 -> 显示左侧/总次数 (暂不支持右侧次数图表)
                else -> {                     // 计重 (WEIGHT_REPS)
                    // [关键逻辑] 如果选了右边(1)，则使用 mode 3 (右侧重量)，否则 mode 1 (左侧重量)
                    if (selectedSide == 1) 3 else 1
                }
            }

            // 4. 图表渲染
            ChartSection(title = title) { granularity ->
                val data by viewModel.getSingleExerciseChartData(selectedExercise, effectiveMode, granularity).collectAsStateWithLifecycle(initialValue = emptyList())
                LineChart(data = data)
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<ChartDataPoint>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val yValues = data.map { it.value }
    val maxVal = (yValues.maxOrNull() ?: 100f).let { if (it == 0f) 100f else it } * 1.2f
    val minVal = 0f
    val yRange = maxVal - minVal

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint().apply {
        color = lineColor.toArgb()
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }

    Canvas(modifier = modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 20.dp)) {
        val width = size.width
        val height = size.height
        val pointSpacing = if (data.size > 1) width / (data.size - 1) else 0f

        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height), Offset(width, height), 2f)

        if (data.size > 1) {
            for (i in 0 until data.size - 1) {
                val x1 = i * pointSpacing
                val y1 = height - ((data[i].value - minVal) / yRange) * height
                val x2 = (i + 1) * pointSpacing
                val y2 = height - ((data[i + 1].value - minVal) / yRange) * height

                drawLine(
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    color = lineColor,
                    strokeWidth = 5f
                )
            }
        }

        for (i in data.indices) {
            val x = i * pointSpacing
            val y = height - ((data[i].value - minVal) / yRange) * height

            drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
            drawCircle(color = lineColor, radius = 7f, center = Offset(x, y))

            val showValue = data.size < 15 || i == 0 || i == data.lastIndex || data[i].value == (yValues.maxOrNull() ?: 0f)

            if (showValue && data[i].value > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", data[i].value),
                    x,
                    y - 20f,
                    valuePaint
                )
            }

            if (data.size < 8 || i % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    data[i].label,
                    x,
                    height + 40f,
                    textPaint
                )
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<ChartDataPoint>,
    barColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val maxVal = (data.maxOfOrNull { it.value } ?: 100f).let { if (it == 0f) 100f else it } * 1.2f

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint().apply {
        color = barColor.toArgb()
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    Canvas(modifier = modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 20.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / data.size) * 0.5f
        val spacing = width / data.size

        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height), Offset(width, height), 2f)

        for (i in data.indices) {
            val barHeight = (data[i].value / maxVal) * height
            val x = i * spacing + (spacing / 2)
            val y = height - barHeight

            drawRect(
                color = barColor,
                topLeft = Offset(x - barWidth/2, y),
                size = Size(barWidth, barHeight)
            )

            if (data[i].value > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.0f", data[i].value),
                    x,
                    y - 10f,
                    valuePaint
                )
            }

            if (data.size < 8 || i % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    data[i].label,
                    x,
                    height + 40f,
                    textPaint
                )
            }
        }
    }
}

// [修改] 像素人热力图组件：改为 Layout 布局以支持点击交互
@Composable
fun PixelBodyHeatmap(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val heatMap by viewModel.muscleHeatMapData.collectAsStateWithLifecycle(initialValue = emptyMap())

    // 状态：当前选中的部位信息 (名称, 原始数值)
    var selectedPartInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun getColorForIntensity(intensity: Float): Color {
        return when {
            intensity <= 0f -> Color.LightGray.copy(alpha = 0.3f)
            intensity < 0.5f -> Color(0xFF81C784) // Light Green
            intensity < 0.8f -> Color(0xFFFFB74D) // Orange
            else -> Color(0xFFE57373) // Red
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chart_title_heatmap),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // [新增] 显示点击选中的部位数据
        Spacer(modifier = Modifier.height(4.dp))
        val infoText = selectedPartInfo?.let { (name, value) ->
            "$name: $value"
        } ?: "" // 默认空
        Text(
            text = infoText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.height(20.dp) // 占位高度防止跳动
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 使用 Column/Row 布局代替 Canvas
        val blockSize = 20.dp
        val gap = 4.dp

        // 网格定义：null 代表空位，String 代表部位 Key
        val gridRows = listOf(
            listOf(null, null, "decoration_head", null, null),
            listOf(null, "part_shoulders", "part_chest", "part_shoulders", null),
            listOf("part_arms", null, "part_back", null, "part_arms"),
            listOf("part_arms", null, "part_abs", null, "part_arms"),
            listOf(null, "part_hips", "part_hips", "part_hips", null),
            listOf(null, "part_thighs", null, "part_thighs", null),
            listOf(null, "part_thighs", null, "part_thighs", null),
            listOf(null, "part_calves", null, "part_calves", null)
        )

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            gridRows.forEach { rowParts ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowParts.forEach { partKey ->
                        if (partKey == null) {
                            // 空位占位符
                            Spacer(modifier = Modifier.size(blockSize))
                        } else {
                            // 实际方块
                            val data = heatMap[partKey]
                            val intensity = data?.intensity ?: 0f
                            val rawValue = data?.volume ?: 0f
                            // 头部作为装饰，特殊处理颜色 (或默认0)
                            val isDecoration = partKey.startsWith("decoration")
                            val color = if (isDecoration) Color.LightGray else getColorForIntensity(intensity)

                            // 获取部位名称资源ID (需要 ExerciseManagerScreen.kt 中的 helper)
                            // 注意：这里需要 ensure getBodyPartResId 是可访问的
                            val labelRes = getBodyPartResId(partKey)
                            val label = if (labelRes != 0) stringResource(labelRes) else partKey

                            Box(
                                modifier = Modifier
                                    .size(blockSize)
                                    .background(color, RoundedCornerShape(4.dp))
                                    .clickable(enabled = !isDecoration) {
                                        // 使用 String.format 给数字加千分位 (%,.0f) 并加上单位
                                        val formattedValue = String.format("%,.0f kg", rawValue)
                                        selectedPartInfo = label to formattedValue
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(), // 或者根据需要调整
            horizontalAlignment = Alignment.CenterHorizontally  // 根据需要调整对齐方式
        ) {
        // 图例
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp)
                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                Text(" 0 ", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Box(Modifier.size(12.dp).background(Color(0xFF81C784), RoundedCornerShape(2.dp)))
                Text(" <50% ", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Box(Modifier.size(12.dp).background(Color(0xFFE57373), RoundedCornerShape(2.dp)))
                Text(" Max ", fontSize = 10.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // [新增] 解释性文本
            Text(
                text = stringResource(R.string.hint_heatmap_volume), // "数值为历史总容量 (重量 x 次数)"
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 14.sp
            )

        }

        }
}
