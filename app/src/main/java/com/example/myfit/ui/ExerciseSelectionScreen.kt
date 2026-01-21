package com.example.myfit.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(viewModel: MainViewModel, navController: NavController) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val context = LocalContext.current

    // 搜索状态
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    // 初始分类
    var selectedCategory by remember { mutableStateOf("STRENGTH") }
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    // 联动 Tab 和 Pager
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
                TopAppBar(
                    title = { Text(stringResource(R.string.title_select_exercise)) },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // 只有非搜索状态才显示 Tab
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
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
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
                // [核心逻辑] 点击即添加
                viewModel.addTaskFromTemplate(template)
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_added_to_plan, template.name),
                    Toast.LENGTH_SHORT
                ).show()
                // 可选：添加后是否自动关闭页面？
                // navController.popBackStack()
            },
            onDelete = null // 选择模式不提供删除功能
        )
    }
}