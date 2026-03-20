package com.lonx.lyrico.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrics.model.Source
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "search_source_priority")
fun SearchSourcePriorityScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var currentList by remember(uiState.searchSourceOrder) {
        mutableStateOf(uiState.searchSourceOrder)
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        currentList = currentList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val tipText = stringResource(id = R.string.search_source_priority_tip)
    val (tipTitle, tipSummary) = remember(tipText) {
        val separators = listOf("。", ". ", "，", ", ")
        val separator = separators.firstOrNull(tipText::contains)
        if (separator == null) {
            tipText to null
        } else {
            tipText.substringBefore(separator).trim() to tipText.substringAfter(separator).trim().ifBlank { null }
        }
    }
    val listViewportHeight = remember(currentList.size) {
        ((currentList.size.coerceAtMost(4) * 88) + 12).dp
    }

    BasicScreenBox(
        title = stringResource(id = R.string.search_source_priority),
        onBack = { navigator.popBackStack() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = Modifier.padding(12.dp),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = tipTitle,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                tipSummary?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                }
            }

            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listViewportHeight),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(
                        items = currentList,
                        key = { _, source -> source.labelRes }
                    ) { index, source ->
                        ReorderableItem(
                            reorderableLazyColumnState,
                            source.labelRes
                        ) { isDragging ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "sourceItemElevation"
                            )
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isDragging) 20.dp else 16.dp,
                                label = "sourceItemCornerRadius"
                            )
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isDragging) {
                                    MiuixTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainer
                                },
                                label = "sourceItemBackground"
                            )
                            val itemShape = RoundedCornerShape(cornerRadius)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .shadow(
                                        elevation = elevation,
                                        shape = itemShape,
                                        clip = false
                                    )
                                    .clip(itemShape)
                                    .background(backgroundColor)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            viewModel.setSearchSourceOrder(currentList)
                                        },
                                        interactionSource = interactionSource
                                    )
                            ) {
                                ReorderableSourceItem(
                                    index = index,
                                    source = source,
                                    isDragging = isDragging,
                                    showDivider = index != currentList.lastIndex
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReorderableSourceItem(
    index: Int,
    source: Source,
    isDragging: Boolean,
    showDivider: Boolean = false
) {
    val badgeColor = if (isDragging) {
        MiuixTheme.colorScheme.secondaryContainerVariant
    } else {
        MiuixTheme.colorScheme.surfaceContainerHighest
    }
    val handleContainerColor = if (isDragging) {
        MiuixTheme.colorScheme.secondaryContainerVariant
    } else {
        MiuixTheme.colorScheme.surfaceContainerHighest
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 36.dp)
                    .background(
                        color = badgeColor,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MiuixTheme.textStyles.body2.fontSize
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(source.labelRes),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = handleContainerColor,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_draghandle_24dp),
                    contentDescription = stringResource(R.string.cd_drag_to_reorder),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }

        if (showDivider && !isDragging) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                color = MiuixTheme.colorScheme.dividerLine,
                thickness = 0.5.dp
            )
        }
    }
}
