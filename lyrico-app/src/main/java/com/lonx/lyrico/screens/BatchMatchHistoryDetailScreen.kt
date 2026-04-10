package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Destination<RootGraph>(route = "batch_match_history_detail")
@Composable
fun BatchMatchHistoryDetailScreen(
    historyId: Long,
    navigator: DestinationsNavigator
) {
    val viewModel: BatchMatchHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val tabs = remember { BatchMatchResult.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    LaunchedEffect(historyId) {
        viewModel.loadHistory(historyId)
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.batch_match_history_detail),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() },
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                TabRowWithContour(
                    tabs = tabs.map { stringResource(it.labelRes) },
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )

            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
                key = { index -> tabs[index].name }
            ) { pageIndex ->
                // 根据当前页面的状态过滤记录
                val currentStatus = tabs[pageIndex]
                val filteredRecords = remember(uiState.allRecords, currentStatus) {
                    uiState.allRecords.filter { it.status == currentStatus }
                }

                LazyColumn(
                    modifier = Modifier
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    overscrollEffect = null,
                ) {
                    if (filteredRecords.isEmpty()) {
                        item {
                            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                                BasicComponent(title = stringResource(R.string.no_record))
                            }
                        }
                    } else {
                        items(
                            items = filteredRecords,
                            key = { it.id }
                        ) { record ->
                            BatchMatchRecordCard(
                                record = record,
                                onClick = {
                                    record.uri?.let {
                                        navigator.navigate(EditMetadataDestination(it))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchMatchRecordCard(
    record: BatchMatchRecordEntity,
    onClick: () -> Unit
) {
    val fileName = record.filePath.substringAfterLast("/")
    val isNavigable = record.uri != null

    Card(
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        ArrowPreference(
            title = fileName,
            summary = record.filePath,
            onClick = if (isNavigable) onClick else null
        )
    }
}
