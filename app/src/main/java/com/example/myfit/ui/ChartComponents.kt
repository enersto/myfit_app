// ChartComponents.kt (请完全覆盖此文件或修改对应函数)

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
            // [修复 Bug 3]：标题布局优化，使用 weight(1f) 允许长标题自动换行，防止挤压按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically // 垂直居中，或者 Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp) // 占据剩余空间
                )

                // 按钮组保持紧凑
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
    mode: Int
) {
    var selectedExercise by remember { mutableStateOf("") }
    val exercises by viewModel.getExerciseNamesByCategory(category).collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by remember { mutableStateOf(false) } // 控制下拉菜单

    if (exercises.isNotEmpty()) {
        if (selectedExercise.isEmpty()) selectedExercise = exercises.first()

        Column {
            // [修复 Bug 2]：将原来的 LazyRow FilterChip 改为 DropdownMenu 下拉选择
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                // 触发按钮 (显示当前选中项 + 下拉箭头)
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(), // 或者 wrapContentWidth
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(selectedExercise, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Exercise")
                }

                // 下拉菜单内容
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 300.dp) // 限制高度，支持滚动
                ) {
                    exercises.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedExercise = name
                                expanded = false
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = if (name == selectedExercise) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 图表区域保持不变
            ChartSection(title = title) { granularity ->
                val data by viewModel.getSingleExerciseChartData(selectedExercise, mode, granularity).collectAsStateWithLifecycle(initialValue = emptyList())
                LineChart(data = data)
            }
        }
    }
}

// ... LineChart 和 BarChart 代码保持不变 (之前版本已提供) ...
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
    val maxVal = (yValues.maxOrNull() ?: 100f) * 1.2f
    val minVal = (yValues.minOrNull() ?: 0f) * 0.8f
    val yRange = if (maxVal - minVal == 0f) 1f else maxVal - minVal

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

            if (data.size < 15 || i == 0 || i == data.lastIndex || data[i].value == yValues.maxOrNull()) {
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

    val maxVal = (data.maxOfOrNull { it.value } ?: 100f) * 1.2f

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