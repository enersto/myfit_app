package com.example.myfit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.viewmodel.MainViewModel
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.mutableIntStateOf // [新增] 用于 var logType by ...
import com.example.myfit.model.LogType           // [新增] 用于引用 LogType 枚举
import androidx.compose.ui.platform.LocalContext

// [修改] 更新部位列表：移除 part_legs，增加 hips, thighs, calves
val BODY_PART_OPTIONS = listOf(
    "part_chest", "part_back", "part_shoulders",
    "part_hips", "part_thighs", "part_calves", // 新增部位
    "part_arms", "part_abs", "part_cardio", "part_other"
)

val EQUIPMENT_OPTIONS = listOf(
    "equip_barbell", "equip_dumbbell", "equip_machine", "equip_cable",
    "equip_bodyweight", "equip_cardio_machine", "equip_kettlebell",
    "equip_smith_machine", "equip_resistance_band", "equip_medicine_ball",
    "equip_trx", "equip_bench", "equip_other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseManagerScreen(viewModel: MainViewModel, navController: NavController) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())

    // 获取当前语言环境，用于重载动作库
    val currentLanguage by viewModel.currentLanguage.collectAsState(initial = "zh")
    val context = LocalContext.current

    // [修改] 状态管理：区分“编辑弹窗”和“详情弹窗”
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    var showDetailDialog by remember { mutableStateOf(false) }
    var viewingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    // [新增] 控制重置警告弹窗
    var showResetDialog by remember { mutableStateOf(false) }

    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 8.dp) // 底部加一点留白
            ) {
                // 第一行：返回按钮 + 标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_manage_exercises),
                        style = MaterialTheme.typography.titleLarge,
                        // 允许标题占据剩余空间，但不强求
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // 第二行：更改语言按钮 (靠右对齐)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp), // 水平内边距对齐
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showResetDialog = true },
                        // 稍微调整按钮高度，使其更紧凑
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_update_lib_lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        bottomBar = {
            TabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                text = stringResource(getCategoryResId(category)),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTemplate = null // 新建模式
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        val groupedData = remember(templates, selectedCategory) {
            templates
                .filter { it.category == selectedCategory }
                .groupBy { it.bodyPart }
                .mapValues { (_, partList) ->
                    partList.groupBy { it.equipment }
                }
                .toSortedMap { a, b -> a.compareTo(b) }
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groupedData.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chart_no_data), color = Color.Gray)
                    }
                }
            } else {
                groupedData.forEach { (bodyPartKey, equipmentMap) ->
                    item(key = bodyPartKey) {
                        ExpandableBodyPartSection(
                            bodyPartKey = bodyPartKey,
                            equipmentMap = equipmentMap,
                            onView = { template ->
                                viewingTemplate = template
                                showDetailDialog = true
                            },
                            onDelete = { viewModel.deleteTemplate(it.id) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // [新增] 重置动作库确认弹窗
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.title_reset_exercises)) },
            text = { Text(stringResource(R.string.msg_reset_exercises_warning)) },
            confirmButton = {
                Button(onClick = {
                    // 调用 ViewModel 进行重载，传入当前语言代码
                    viewModel.reloadStandardExercises(context, currentLanguage)
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // 详情弹窗
    if (showDetailDialog && viewingTemplate != null) {
        ExerciseDetailDialog(
            template = viewingTemplate!!,
            onDismiss = { showDetailDialog = false },
            onEdit = {
                editingTemplate = viewingTemplate
                viewingTemplate = null
                showDetailDialog = false
                showEditDialog = true
            }
        )
    }

    // 编辑弹窗
    if (showEditDialog) {
        ExerciseEditDialog(
            template = editingTemplate,
            onDismiss = { showEditDialog = false },
            onSave = { temp ->
                viewModel.saveTemplate(temp)
                showEditDialog = false
            }
        )
    }
}

// [新增] 动作详情展示页面 (只读，紧凑展示)
@Composable
fun ExerciseDetailDialog(
    template: ExerciseTemplate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(template.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.weight(1f))
                // 类别标签
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(getCategoryResId(template.category)),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 1. 属性标签行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailChip(stringResource(getBodyPartResId(template.bodyPart)))
                    DetailChip(stringResource(getEquipmentResId(template.equipment)))
                    if (template.isUnilateral) {
                        DetailChip(stringResource(R.string.label_is_unilateral), Color(0xFFFF9800))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 核心信息列表
                DetailInfoRow(stringResource(R.string.label_target), template.defaultTarget)
                DetailInfoRow(stringResource(R.string.label_log_type), stringResource(getLogTypeResId(template.logType)))

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // 3. 动作说明
                Text(
                    text = stringResource(R.string.label_instruction),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (template.instruction.isNotBlank()) template.instruction else stringResource(R.string.label_instruction_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (template.instruction.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.btn_edit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel)) // 或者用 btn_close 如果有
            }
        }
    )
}

@Composable
fun DetailChip(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ExpandableBodyPartSection(
    bodyPartKey: String,
    equipmentMap: Map<String, List<ExerciseTemplate>>,
    onView: (ExerciseTemplate) -> Unit, // [修改] 参数名 onEdit -> onView
    onDelete: (ExerciseTemplate) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    val bodyPartRes = getBodyPartResId(bodyPartKey)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else bodyPartKey

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = bodyPartLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val count = equipmentMap.values.sumOf { it.size }
                    Text("($count)", color = Color.Gray, fontSize = 12.sp)
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotationState)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    equipmentMap.forEach { (equipKey, templates) ->
                        EquipmentGroup(equipKey, templates, onView, onDelete)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EquipmentGroup(
    equipKey: String,
    templates: List<ExerciseTemplate>,
    onView: (ExerciseTemplate) -> Unit, // [修正] 参数名为 onView
    onDelete: (ExerciseTemplate) -> Unit
) {
    val equipRes = getEquipmentResId(equipKey)
    val equipLabel = if (equipRes != 0) stringResource(equipRes) else equipKey

    Column {
        Text(
            text = equipLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        )

        templates.forEach { template ->
            ExerciseMinimalCard(template, { onView(template) }, { onDelete(template) })
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun ExerciseMinimalCard(template: ExerciseTemplate, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = template.name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (template.isUnilateral) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.tag_uni),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (template.defaultTarget.isNotEmpty()) {
                        Text(text = template.defaultTarget, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// [新增] 获取 LogType 的资源 ID
fun getLogTypeResId(type: Int): Int {
    return when (type) {
        LogType.DURATION.value -> R.string.log_type_duration
        LogType.REPS_ONLY.value -> R.string.log_type_reps_only
        else -> R.string.log_type_weight_reps
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditDialog(template: ExerciseTemplate?, onDismiss: () -> Unit, onSave: (ExerciseTemplate) -> Unit) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var target by remember { mutableStateOf(template?.defaultTarget ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }
    var bodyPart by remember { mutableStateOf(template?.bodyPart ?: "part_chest") }
    var equipment by remember { mutableStateOf(template?.equipment ?: "equip_barbell") }
    var isUnilateral by remember { mutableStateOf(template?.isUnilateral ?: false) }

    // [新增] 动作说明状态
    var instruction by remember { mutableStateOf(template?.instruction ?: "") }

    // [新增] 记录类型状态
    var logType by remember { mutableIntStateOf(template?.logType ?: LogType.WEIGHT_REPS.value) }

    // 当 Category 变化时，重置 logType 到合理的默认值 (仅新建时)
    LaunchedEffect(category) {
        if (template == null) {
            logType = when (category) {
                "CARDIO" -> LogType.DURATION.value
                "CORE" -> LogType.DURATION.value
                else -> LogType.WEIGHT_REPS.value
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) stringResource(R.string.title_new_exercise) else stringResource(R.string.title_edit_exercise)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 1. 基本信息
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.label_target)) },
                    placeholder = { Text(stringResource(R.string.hint_target)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 2. 类别选择
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("STRENGTH", "CARDIO", "CORE").forEach { cat ->
                        CategoryRadio(
                            label = stringResource(getCategoryResId(cat)),
                            selected = category == cat
                        ) { category = cat }
                    }
                }

                // 3. 单边选项 (仅力量)
                if (category == "STRENGTH") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isUnilateral = !isUnilateral }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isUnilateral, onCheckedChange = { isUnilateral = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.label_is_unilateral), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // 4. 部位与器械
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_body_part), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(currentKey = bodyPart, options = BODY_PART_OPTIONS, onSelect = { bodyPart = it })

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_equipment), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(currentKey = equipment, options = EQUIPMENT_OPTIONS, onSelect = { equipment = it })

                // 5. [修改] 记录方式 (改为垂直排列，防止挤压)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.label_log_type),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (category == "CARDIO") {
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text(stringResource(R.string.log_type_duration)) },
                            leadingIcon = { Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp)) }
                        )
                    } else {
                        // 力量和核心通用的选项
                        FilterChip(
                            selected = logType == LogType.WEIGHT_REPS.value,
                            onClick = { logType = LogType.WEIGHT_REPS.value },
                            label = { Text(stringResource(R.string.log_type_weight_reps)) },
                            leadingIcon = { Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = logType == LogType.REPS_ONLY.value,
                            onClick = { logType = LogType.REPS_ONLY.value },
                            label = { Text(stringResource(R.string.log_type_reps_only)) },
                            leadingIcon = { Icon(Icons.Default.AccessibilityNew, null, modifier = Modifier.size(16.dp)) }
                        )

                        // 核心专属选项
                        if (category == "CORE") {
                            FilterChip(
                                selected = logType == LogType.DURATION.value,
                                onClick = { logType = LogType.DURATION.value },
                                label = { Text(stringResource(R.string.log_type_duration)) },
                                leadingIcon = { Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                // 6. [新增] 动作说明输入框
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.label_instruction)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, // 默认显示3行高度
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(
                    ExerciseTemplate(
                        id = template?.id ?: 0,
                        name = name,
                        defaultTarget = target,
                        category = category,
                        bodyPart = bodyPart,
                        equipment = equipment,
                        isUnilateral = isUnilateral,
                        logType = logType,      // 保存记录类型
                        instruction = instruction // 保存动作说明
                    )
                )
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun CategoryRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDropdown(currentKey: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val currentResId = getBodyPartResId(currentKey).takeIf { it != 0 } ?: getEquipmentResId(currentKey)
    val displayText = if (currentResId != 0) stringResource(currentResId) else currentKey

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { key ->
                val resId = getBodyPartResId(key).takeIf { it != 0 } ?: getEquipmentResId(key)
                val label = if (resId != 0) stringResource(resId) else key
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(key); expanded = false }
                )
            }
        }
    }
}

// Helper functions (Added to ensure they are available)

fun getCategoryResId(key: String): Int = when(key) {
    "STRENGTH" -> R.string.category_strength
    "CARDIO" -> R.string.category_cardio
    "CORE" -> R.string.category_core
    else -> R.string.category_strength
}

// [修改] 更新资源映射函数
fun getBodyPartResId(key: String): Int = when(key) {
    "part_chest" -> R.string.part_chest
    "part_back" -> R.string.part_back
    "part_legs" -> R.string.part_thighs // 兼容旧数据：如果读到旧腿部，显示为大腿
    "part_thighs" -> R.string.part_thighs // [新增]
    "part_hips" -> R.string.part_hips     // [新增]
    "part_calves" -> R.string.part_calves // [新增]
    "part_shoulders" -> R.string.part_shoulders
    "part_arms" -> R.string.part_arms
    "part_abs" -> R.string.part_abs
    "part_cardio" -> R.string.part_cardio
    "part_other" -> R.string.part_other
    else -> 0
}

fun getEquipmentResId(key: String): Int = when(key) {
    "equip_barbell" -> R.string.equip_barbell
    "equip_dumbbell" -> R.string.equip_dumbbell
    "equip_machine" -> R.string.equip_machine
    "equip_cable" -> R.string.equip_cable
    "equip_bodyweight" -> R.string.equip_bodyweight
    "equip_cardio_machine" -> R.string.equip_cardio_machine
    "equip_kettlebell" -> R.string.equip_kettlebell
    "equip_smith_machine" -> R.string.equip_smith_machine
    "equip_resistance_band" -> R.string.equip_resistance_band
    "equip_medicine_ball" -> R.string.equip_medicine_ball
    "equip_trx" -> R.string.equip_trx
    "equip_bench" -> R.string.equip_bench
    "equip_other" -> R.string.equip_other
    else -> 0
}