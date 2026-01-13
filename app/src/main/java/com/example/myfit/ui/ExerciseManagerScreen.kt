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

val BODY_PART_OPTIONS = listOf(
    "part_chest", "part_back", "part_legs", "part_shoulders",
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
    var showDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_manage_exercises),
                        style = MaterialTheme.typography.titleLarge
                    )
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
                    editingTemplate = null
                    showDialog = true
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
                            onEdit = { editingTemplate = it; showDialog = true },
                            onDelete = { viewModel.deleteTemplate(it.id) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showDialog) {
        ExerciseEditDialog(
            template = editingTemplate,
            onDismiss = { showDialog = false },
            onSave = { temp ->
                viewModel.saveTemplate(temp)
                showDialog = false
            }
        )
    }
}

@Composable
fun ExpandableBodyPartSection(
    bodyPartKey: String,
    equipmentMap: Map<String, List<ExerciseTemplate>>,
    onEdit: (ExerciseTemplate) -> Unit,
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
                        EquipmentGroup(equipKey, templates, onEdit, onDelete)
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
    onEdit: (ExerciseTemplate) -> Unit,
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
            ExerciseMinimalCard(template, { onEdit(template) }, { onDelete(template) })
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun ExerciseMinimalCard(template: ExerciseTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditDialog(template: ExerciseTemplate?, onDismiss: () -> Unit, onSave: (ExerciseTemplate) -> Unit) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var target by remember { mutableStateOf(template?.defaultTarget ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }
    var bodyPart by remember { mutableStateOf(template?.bodyPart ?: "part_chest") }
    var equipment by remember { mutableStateOf(template?.equipment ?: "equip_barbell") }
    var isUnilateral by remember { mutableStateOf(template?.isUnilateral ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) stringResource(R.string.title_new_exercise) else stringResource(R.string.title_edit_exercise)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("STRENGTH", "CARDIO", "CORE").forEach { cat ->
                        CategoryRadio(
                            label = stringResource(getCategoryResId(cat)),
                            selected = category == cat
                        ) { category = cat }
                    }
                }

                // [新增] 单边动作选项 (仅力量动作显示)
                if (category == "STRENGTH") {
                    Spacer(modifier = Modifier.height(12.dp))
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

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_body_part), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(
                    currentKey = bodyPart,
                    options = BODY_PART_OPTIONS,
                    onSelect = { bodyPart = it }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_equipment), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(
                    currentKey = equipment,
                    options = EQUIPMENT_OPTIONS,
                    onSelect = { equipment = it }
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
                        isUnilateral = isUnilateral // [新增]
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
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

fun getBodyPartResId(key: String): Int = when(key) {
    "part_chest" -> R.string.part_chest
    "part_back" -> R.string.part_back
    "part_legs" -> R.string.part_legs
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