package com.example.myfit.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val tasks by viewModel.historyRecords.collectAsState(initial = emptyList())
    // V4.2: 获取体重历史
    val weights by viewModel.weightHistory.collectAsState(initial = emptyList())

    // V4.2: 合并数据逻辑
    // 1. 找出所有出现过的日期 (无论是训练过 还是 称过重)
    val historyData = remember(tasks, weights) {
        val allDates = (tasks.map { it.date } + weights.map { it.date })
            .distinct()
            .sortedDescending() // 按日期倒序

        // 2. 映射为 UI 需要的结构：日期 -> (当日体重?, 当日训练列表)
        allDates.map { date ->
            val dayWeight = weights.find { it.date == date }
            val dayTasks = tasks.filter { it.date == date }
            Triple(date, dayWeight, dayTasks)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("历史记录", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        if (historyData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无记录，快去锻炼吧！", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {

                // 遍历合并后的数据
                historyData.forEach { (date, weightRecord, dayTasks) ->
                    item {
                        Column {
                            // 日期标题
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

                                // V4.2: 如果当天有体重记录，在这里显示一个小标签
                                if (weightRecord != null) {
                                    Surface(
                                        color = Color(0xFFFF9800).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MonitorWeight, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
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

                    // 渲染当天的训练列表
                    if (dayTasks.isEmpty()) {
                        // 如果这一天只有体重没有训练（比如休息日），显示一行小字
                        item {
                            Text("（当日无训练记录）", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    } else {
                        items(dayTasks) { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(task.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}" else task.target,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Text("完成", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}