package com.example.myfit.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.LogType
import com.example.myfit.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Date

// 更新部位列表
val BODY_PART_OPTIONS = listOf(
    "part_chest", "part_back", "part_shoulders",
    "part_hips", "part_thighs", "part_calves",
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
    val currentLanguage by viewModel.currentLanguage.collectAsState(initial = "zh")
    val context = LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    var showDetailDialog by remember { mutableStateOf(false) }
    var viewingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    var showResetDialog by remember { mutableStateOf(false) }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }
    val pagerState = rememberPagerState(pageCount = { categories.size })

    LaunchedEffect(selectedCategory) {
        pagerState.animateScrollToPage(categories.indexOf(selectedCategory))
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedCategory = categories[pagerState.currentPage]
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                ExerciseSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onBack = {
                        isSearching = false
                        searchQuery = ""
                    },
                    onClear = { searchQuery = "" }
                )
            } else {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 8.dp)
                ) {
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
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showResetDialog = true },
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
            }
        },
        bottomBar = {
            if (!isSearching) {
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
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTemplate = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        ExerciseListContent(
            padding = padding,
            searchQuery = searchQuery,
            templates = templates,
            pagerState = pagerState,
            categories = categories,
            onItemClick = { template ->
                viewingTemplate = template
                showDetailDialog = true
            },
            onDelete = { template ->
                viewModel.deleteTemplate(template.id)
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.title_reset_exercises)) },
            text = { Text(stringResource(R.string.msg_reset_exercises_warning)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.reloadStandardExercises(context, currentLanguage)
                    showResetDialog = false
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditDialog(template: ExerciseTemplate?, onDismiss: () -> Unit, onSave: (ExerciseTemplate) -> Unit) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var target by remember { mutableStateOf(template?.defaultTarget ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }
    var bodyPart by remember { mutableStateOf(template?.bodyPart ?: "part_chest") }
    var equipment by remember { mutableStateOf(template?.equipment ?: "equip_barbell") }
    var isUnilateral by remember { mutableStateOf(template?.isUnilateral ?: false) }
    var instruction by remember { mutableStateOf(template?.instruction ?: "") }
    var logType by remember { mutableIntStateOf(template?.logType ?: LogType.WEIGHT_REPS.value) }
    var imageUri by remember { mutableStateOf(template?.imageUri) }

    val context = LocalContext.current

    LaunchedEffect(category) {
        if (template == null) {
            logType = when (category) {
                "CARDIO" -> LogType.DURATION.value
                "CORE" -> LogType.DURATION.value
                else -> LogType.WEIGHT_REPS.value
            }
        }
    }

    fun saveImageToInternal(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val internalPath = saveImageToInternal(it)
            if (internalPath != null) imageUri = internalPath
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempPictureUri(): Uri? {
        return try {
            val tempFile = File.createTempFile("camera_img_", ".jpg", context.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            val internalPath = saveImageToInternal(tempCameraUri!!)
            if (internalPath != null) imageUri = internalPath
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val uri = createTempPictureUri()
                if (uri != null) {
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                }
            } else {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) stringResource(R.string.title_new_exercise) else stringResource(R.string.title_edit_exercise)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = if (imageUri!!.startsWith("/")) File(imageUri!!) else imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val permission = Manifest.permission.CAMERA
                                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                    val uri = createTempPictureUri()
                                    if (uri != null) {
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Text(stringResource(R.string.source_camera), color = Color.White, fontSize = 12.sp)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { galleryLauncher.launch("image/*") }
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Text(stringResource(R.string.source_gallery), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

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
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("STRENGTH", "CARDIO", "CORE").forEach { cat ->
                        CategoryRadio(
                            label = stringResource(getCategoryResId(cat)),
                            selected = category == cat
                        ) { category = cat }
                    }
                }

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

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_body_part), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(currentKey = bodyPart, options = BODY_PART_OPTIONS, onSelect = { bodyPart = it })

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.label_equipment), style = MaterialTheme.typography.bodyMedium)
                ResourceDropdown(currentKey = equipment, options = EQUIPMENT_OPTIONS, onSelect = { equipment = it })

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

                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.label_instruction)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
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
                        logType = logType,
                        instruction = instruction,
                        imageUri = imageUri
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