package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState // 🟢 新增
import androidx.compose.foundation.verticalScroll     // 🟢 新增
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun ExerciseManagerScreen(navController: NavController, viewModel: MainViewModel) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.title_manage_exercises), style = MaterialTheme.typography.titleLarge)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingTemplate = null; showDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(templates) { template ->
                ExerciseItemCard(template = template, onEdit = { editingTemplate = template; showDialog = true }, onDelete = { viewModel.deleteTemplate(template.id) })
            }
        }
    }

    if (showDialog) {
        ExerciseEditDialog(template = editingTemplate, onDismiss = { showDialog = false }, onSave = { temp -> viewModel.saveTemplate(temp); showDialog = false })
    }
}

@Composable
fun ExerciseItemCard(template: ExerciseTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    // 翻译显示
    val categoryLabel = when (template.category) {
        "STRENGTH" -> stringResource(R.string.category_strength)
        "CARDIO" -> stringResource(R.string.category_cardio)
        "CORE" -> stringResource(R.string.category_core)
        else -> template.category
    }
    // 尝试获取部位的资源显示，如果是自定义字符串则直接显示
    val bodyPartRes = getBodyPartResId(template.bodyPart)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else template.bodyPart

    val equipmentRes = getEquipmentResId(template.equipment)
    val equipLabel = if (equipmentRes != 0) stringResource(equipmentRes) else template.equipment

    Card(modifier = Modifier.fillMaxWidth().clickable { onEdit() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelTag(categoryLabel, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (bodyPartLabel.isNotEmpty()) {
                        LabelTag(bodyPartLabel, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (equipLabel.isNotEmpty()) {
                        LabelTag(equipLabel, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
fun LabelTag(text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = contentColor)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) stringResource(R.string.title_new_exercise) else stringResource(R.string.title_edit_exercise)) },
        text = {
            Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text(stringResource(R.string.label_target)) }, placeholder = { Text(stringResource(R.string.hint_target)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CategoryRadio(stringResource(R.string.category_strength), category == "STRENGTH") { category = "STRENGTH" }
                    CategoryRadio(stringResource(R.string.category_cardio), category == "CARDIO") { category = "CARDIO" }
                    CategoryRadio(stringResource(R.string.category_core), category == "CORE") { category = "CORE" }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // 部位选择器
                Text(stringResource(R.string.label_body_part), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(
                    currentKey = bodyPart,
                    options = listOf("part_chest", "part_back", "part_legs", "part_shoulders", "part_arms", "part_abs", "part_cardio", "part_other"),
                    onSelect = { bodyPart = it }
                )

                Spacer(modifier = Modifier.height(16.dp))
                // 器械选择器
                Text(stringResource(R.string.label_equipment), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(
                    currentKey = equipment,
                    options = listOf("equip_barbell", "equip_dumbbell", "equip_machine", "equip_cable", "equip_bodyweight", "equip_cardio_machine"),
                    onSelect = { equipment = it }
                )
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(ExerciseTemplate(id = template?.id ?: 0, name = name, defaultTarget = target, category = category, bodyPart = bodyPart, equipment = equipment)) }) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun CategoryRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp)) // 缩小一点
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDropdown(currentKey: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // 获取当前Key对应的显示文本
    val currentResId = getBodyPartResId(currentKey).takeIf { it != 0 } ?: getEquipmentResId(currentKey)
    val displayText = if (currentResId != 0) stringResource(currentResId) else currentKey

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { key ->
                val resId = getBodyPartResId(key).takeIf { it != 0 } ?: getEquipmentResId(key)
                val label = if (resId != 0) stringResource(resId) else key
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(key); expanded = false })
            }
        }
    }
}

// 辅助函数：根据 Key 查找资源 ID
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
    else -> 0
}