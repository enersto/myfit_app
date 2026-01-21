package com.example.myfit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.LogType
import java.io.File

// [移动至此] 详情弹窗：现在打卡页和管理页通用
@Composable
fun ExerciseDetailDialog(
    template: ExerciseTemplate,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    var showFullImage by remember { mutableStateOf(false) }

    if (showFullImage && !template.imageUri.isNullOrBlank()) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Box(modifier = Modifier.fillMaxSize().clickable { showFullImage = false }) {
                AsyncImage(
                    model = if (template.imageUri!!.startsWith("/")) File(template.imageUri!!) else template.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(template.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(modifier = Modifier.weight(1f))
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
                    if (!template.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = if (template.imageUri!!.startsWith("/")) File(template.imageUri!!) else template.imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFullImage = true },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailChip(stringResource(getBodyPartResId(template.bodyPart)))
                        DetailChip(stringResource(getEquipmentResId(template.equipment)))
                        if (template.isUnilateral) {
                            DetailChip(stringResource(R.string.tag_uni), Color(0xFFFF9800))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DetailInfoRow(stringResource(R.string.label_target), template.defaultTarget)
                    DetailInfoRow(stringResource(R.string.label_log_type), stringResource(getLogTypeResId(template.logType)))

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

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
                if (onEdit != null) {
                    Button(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_edit))
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_confirm))
                    }
                }
            },
            dismissButton = {
                if (onEdit != null) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        )
    }
}

@Composable
fun ExerciseSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    placeholder: String = stringResource(R.string.hint_search_exercise)
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 16.dp),
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }

        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
            }
        }
    }
}

@Composable
fun ExerciseListContent(
    padding: PaddingValues,
    searchQuery: String,
    templates: List<ExerciseTemplate>,
    pagerState: PagerState,
    categories: List<String>,
    onItemClick: (ExerciseTemplate) -> Unit,
    onDelete: ((ExerciseTemplate) -> Unit)? = null
) {
    if (searchQuery.isBlank()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            val currentCat = categories[pageIndex]
            val groupedData = remember(templates, currentCat) {
                templates
                    .filter { it.category == currentCat }
                    .groupBy { it.bodyPart }
                    .mapValues { (_, partList) ->
                        partList.groupBy { it.equipment }
                    }
                    .toSortedMap { a, b -> a.compareTo(b) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                                onItemClick = onItemClick,
                                onDelete = onDelete
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    } else {
        val filteredList = remember(templates, searchQuery) {
            templates.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.bodyPart.contains(searchQuery, ignoreCase = true) ||
                        it.equipment.contains(searchQuery, ignoreCase = true)
            }
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_search_results), color = Color.Gray)
                    }
                }
            } else {
                items(filteredList) { template ->
                    ExerciseMinimalCard(
                        template = template,
                        onClick = { onItemClick(template) },
                        onDelete = if (onDelete != null) { { onDelete(template) } } else null
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableBodyPartSection(
    bodyPartKey: String,
    equipmentMap: Map<String, List<ExerciseTemplate>>,
    onItemClick: (ExerciseTemplate) -> Unit,
    onDelete: ((ExerciseTemplate) -> Unit)?
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
                                ExerciseMinimalCard(
                                    template = template,
                                    onClick = { onItemClick(template) },
                                    onDelete = if (onDelete != null) { { onDelete(template) } } else null
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseMinimalCard(
    template: ExerciseTemplate,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
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
            if (!template.imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = if (template.imageUri!!.startsWith("/")) File(template.imageUri!!) else template.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = template.name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = stringResource(getCategoryResId(template.category)),
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (template.isUnilateral) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.tag_uni),
                                fontSize = 8.sp,
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
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
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

fun getCategoryResId(key: String): Int = when(key) {
    "STRENGTH" -> R.string.category_strength
    "CARDIO" -> R.string.category_cardio
    "CORE" -> R.string.category_core
    else -> R.string.category_strength
}

fun getBodyPartResId(key: String): Int = when(key) {
    "part_chest" -> R.string.part_chest
    "part_back" -> R.string.part_back
    "part_legs" -> R.string.part_thighs
    "part_thighs" -> R.string.part_thighs
    "part_hips" -> R.string.part_hips
    "part_calves" -> R.string.part_calves
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

fun getLogTypeResId(type: Int): Int {
    return when (type) {
        LogType.DURATION.value -> R.string.log_type_duration
        LogType.REPS_ONLY.value -> R.string.log_type_reps_only
        else -> R.string.log_type_weight_reps
    }
}