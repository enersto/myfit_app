package com.example.myfit.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(navController: NavController, viewModel: MainViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()

    val context = LocalContext.current

    val dao = remember { AppDatabase.getDatabase(context).workoutDao() }
    val scheduleList by dao.getAllSchedules().collectAsState(initial = emptyList())

    var showImportDialog by remember { mutableStateOf(false) }
    var showManualRoutineDialog by remember { mutableStateOf(false) }

    // File pickers for backup/restore
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
        uri?.let { viewModel.backupDatabase(it, context) }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreDatabase(it, context) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Advanced Features Title
        item {
            Text(stringResource(R.string.settings_advanced), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }

        // 2. Language Settings
        item {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("中文", "zh", currentLanguage) { viewModel.switchLanguage("zh"); (context as? Activity)?.recreate() }
                LanguageChip("EN", "en", currentLanguage) { viewModel.switchLanguage("en"); (context as? Activity)?.recreate() }
                LanguageChip("ES", "es", currentLanguage) { viewModel.switchLanguage("es"); (context as? Activity)?.recreate() }
                LanguageChip("JA", "ja", currentLanguage) { viewModel.switchLanguage("ja"); (context as? Activity)?.recreate() }
                LanguageChip("DE", "de", currentLanguage) { viewModel.switchLanguage("de"); (context as? Activity)?.recreate() }
            }
        }

        // 3. Data Management
        item {
            Text(stringResource(R.string.settings_data_management), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))

            // CSV Operations
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.exportHistoryToCsv(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.export_csv_btn), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.import_csv_btn), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Database Operations
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    createBackupLauncher.launch("myfit_backup_${java.time.LocalDate.now()}.db")
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.btn_backup_db), fontSize = 12.sp)
                }
                OutlinedButton(onClick = {
                    restoreBackupLauncher.launch(arrayOf("application/*"))
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.btn_restore_db), fontSize = 12.sp)
                }
            }
        }

        // 4. Weekly Routine Button
        item {
            Button(
                onClick = { showManualRoutineDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.EditCalendar, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.weekly_plan), color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Text(">", color = Color.White.copy(alpha = 0.7f))
            }
        }

        // 5. Exercise Library Button
        item {
            Button(
                onClick = { navController.navigate("exercise_manager") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.manage_lib), color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Text(">", color = Color.Gray)
            }
        }

        // 6. Theme Switcher
        item {
            Text(stringResource(R.string.theme_style), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTheme.values().forEach { theme ->
                    ThemeCircle(theme, currentTheme == theme) { viewModel.switchTheme(theme) }
                }
            }
            Divider(modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }

        // 7. Schedule Type Title
        item {
            Text(stringResource(R.string.schedule_type_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }

        // 8. Schedule List
        items(scheduleList) { config ->
            ScheduleItem(config) { newType ->
                viewModel.updateScheduleConfig(config.dayOfWeek, newType)
            }
        }

        // 9. About Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            AboutSection()
        }
    }

    // Dialog Logic
    if (showImportDialog) {
        val defaultCsv = stringResource(R.string.import_csv_template)

        ImportDialog(defaultText = defaultCsv, onDismiss = { showImportDialog = false }) { csv ->
            // ✅ 修改处：传入当前的 context
            viewModel.importWeeklyRoutine(context, csv)
            showImportDialog = false
        }
    }

    if (showManualRoutineDialog) {
        ManualRoutineDialog(viewModel, onDismiss = { showManualRoutineDialog = false })
    }
}

// ================== Helper Components ==================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRoutineDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var selectedDay by remember { mutableStateOf(1) }
    val routineItems = remember(selectedDay) { mutableStateListOf<WeeklyRoutineItem>() }
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedDay) {
        routineItems.clear()
        routineItems.addAll(viewModel.getRoutineForDay(selectedDay))
    }

    var showTemplateSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weekly_plan))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = selectedDay - 1, edgePadding = 0.dp, containerColor = Color.Transparent) {
                    (1..7).forEach { day ->
                        val weekRes = when(day) {
                            1 -> R.string.week_1; 2 -> R.string.week_2; 3 -> R.string.week_3; 4 -> R.string.week_4
                            5 -> R.string.week_5; 6 -> R.string.week_6; 7 -> R.string.week_7; else -> R.string.week_1
                        }
                        Tab(selected = selectedDay == day, onClick = { selectedDay = day }, text = { Text(stringResource(weekRes)) })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (routineItems.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_plan), color = Color.Gray) }
                    } else {
                        LazyColumn {
                            items(routineItems) { item ->
                                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(item.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    IconButton(onClick = { viewModel.removeRoutineItem(item); routineItems.remove(item) }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f))
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
                Button(onClick = { showTemplateSelector = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.btn_add))
                }
            }
        },
        confirmButton = {}
    )

    if (showTemplateSelector) {
        val categories = listOf("STRENGTH", "CARDIO", "CORE")
        var selectedCategory by remember { mutableStateOf("STRENGTH") }

        ModalBottomSheet(onDismissRequest = { showTemplateSelector = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.manage_lib),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )

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

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    val filtered = allTemplates.filter { it.category == selectedCategory }

                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.chart_no_data), color = Color.Gray)
                            }
                        }
                    } else {
                        items(filtered) { template ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addRoutineItem(selectedDay, template)
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
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ImportDialog(defaultText: String, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf(defaultText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_dialog_hint), fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }) { Text(stringResource(R.string.import_btn)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageChip(label: String, code: String, currentCode: String, onClick: () -> Unit) {
    FilterChip(selected = code == currentCode, onClick = onClick, label = { Text(label) })
}

@Composable
fun ScheduleItem(config: ScheduleConfig, onTypeChange: (DayType) -> Unit) {
    val weekRes = when(config.dayOfWeek) {
        1 -> R.string.week_1; 2 -> R.string.week_2; 3 -> R.string.week_3; 4 -> R.string.week_4; 5 -> R.string.week_5; 6 -> R.string.week_6; 7 -> R.string.week_7; else -> R.string.week_1
    }
    val typeName = stringResource(config.dayType.labelResId)
    Card(modifier = Modifier.fillMaxWidth().clickable { onTypeChange(DayType.values()[(config.dayType.ordinal + 1) % DayType.values().size]) }, colors = CardDefaults.cardColors(containerColor = Color(config.dayType.colorHex).copy(alpha = 0.9f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(weekRes), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(typeName, color = Color.White)
        }
    }
}

@Composable
fun ThemeCircle(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(theme.primary))
            .border(width = 3.dp, color = borderColor, shape = CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = stringResource(R.string.about),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.about_version_format, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_credit),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}