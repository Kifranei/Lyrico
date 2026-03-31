package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDetailDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Destination<RootGraph>(route = "batch_match_history")
@Composable
fun BatchMatchHistoryScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchMatchHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // 使用 remember 缓存 SimpleDateFormat 以提高性能
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val showConfirmDialog = remember { mutableStateOf(false) }
    var selectedHistoryId by remember { mutableStateOf<Long?>(null) }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.batch_match_history_title),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = {
                            navigator.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        if (showConfirmDialog.value && selectedHistoryId != null) {
            SuperDialog(
                title = stringResource(R.string.batch_match_delete_title),
                show = showConfirmDialog.value,
                onDismissRequest = {
                    showConfirmDialog.value = false
                    selectedHistoryId = null
                }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.batch_match_delete_message),
                        modifier = Modifier.fillMaxWidth(),
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showConfirmDialog.value = false
                                selectedHistoryId = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                selectedHistoryId?.let(viewModel::deleteHistory)
                                showConfirmDialog.value = false
                                selectedHistoryId = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            if (uiState.historyList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.batch_match_history_empty)
                        )
                    }
                }
            } else {
                items(
                    items = uiState.historyList,
                    key = { it.id }
                ) { history ->
                    BatchMatchHistoryCard(
                        modifier = Modifier.animateItem(),
                        formattedDate = dateFormat.format(Date(history.timestamp)),
                        summary = stringResource(
                            R.string.batch_match_stat_format,
                            history.successCount,
                            history.failureCount,
                            history.skippedCount
                        )+"\n"+stringResource(
                            R.string.batch_match_duration_format,
                            history.durationMillis / 1000.0
                        ),
                        onClick = {
                            navigator.navigate(BatchMatchHistoryDetailDestination(history.id))
                        },
                        onDeleteClick = {
                            selectedHistoryId = history.id
                            showConfirmDialog.value = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchMatchHistoryCard(
    modifier: Modifier = Modifier,
    formattedDate: String,
    summary: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        BasicComponent(
            title = formattedDate,
            summary = summary,
            endActions = {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            },
            onClick = onClick
        )
    }
}
