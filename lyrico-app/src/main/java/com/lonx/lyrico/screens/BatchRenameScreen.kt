package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.RenamePreview
import com.ramcosta.composedestinations.generated.destinations.CharacterMappingDestination
import com.lonx.lyrico.utils.TagField
import com.lonx.lyrico.viewmodel.BatchRenameViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
@Destination<RootGraph>(route = "batch_rename")
fun BatchRenameScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchRenameViewModel = koinViewModel()
    val renameFormat by viewModel.renameFormat.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showDropdowns = remember { mutableStateOf(false) }


    var showPlaceholderInfo by remember { mutableStateOf(false) }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.batch_rename_title),
                navigationIcon = {
                    IconButton(
                        onClick = { if (!uiState.isRenamingInProgress) navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!uiState.isRenamingInProgress) {
                                navigator.navigate(CharacterMappingDestination)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ListView,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        }
    ) { paddingValues ->
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
            overscrollEffect = null,
        ) {
            item {
                SmallTitle(text = stringResource(id = R.string.rename_format))
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    TextField(
                        modifier = Modifier.padding(12.dp),
                        value = renameFormat,
                        onValueChange = { viewModel.saveFormat(it) },
                        trailingIcon = {
                            Box() {
                                IconButton(
                                    modifier = Modifier.padding(end = 12.dp),
                                    onClick = { showDropdowns.value = true }) {
                                    Icon(
                                        imageVector = MiuixIcons.Notes,
                                        contentDescription = null
                                    )
                                }
                                OverlayListPopup(
                                    show = showDropdowns.value,
                                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                    onDismissRequest = { showDropdowns.value = false },
                                    alignment = PopupPositionProvider.Align.TopEnd
                                ) {
                                    ListPopupColumn {
                                        uiState.presetFormats.forEach { format ->
                                            DropdownImpl(
                                                text = format,
                                                isSelected = renameFormat == format,
                                                optionSize = uiState.presetFormats.size,
                                                index = uiState.presetFormats.indexOf(format),
                                                onSelectedIndexChange = {
                                                    viewModel.saveFormat(format)
                                                    showDropdowns.value = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = stringResource(id = R.string.format_hint),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.format_preset_show_placeholders),
                        checked = showPlaceholderInfo,
                        onCheckedChange = { showPlaceholderInfo = it }
                    )
                    AnimatedVisibility(visible = showPlaceholderInfo) {
                        PlaceholderInfoContent()
                    }

                    // 重命名进度
                    if (uiState.isRenamingInProgress) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.batch_edit_saving_progress,
                                    uiState.saveProgress,
                                    uiState.saveTotal
                                ),
                                style = MiuixTheme.textStyles.body1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = if (uiState.saveTotal > 0)
                                    uiState.saveProgress.toFloat() / uiState.saveTotal
                                else 0f
                            )
                        }
                    }

                    TextButton(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        onClick = { viewModel.executeRename() },
                        text = stringResource(id = R.string.action_rename),
                        enabled = uiState.previews.isNotEmpty() && !uiState.isRenamingInProgress,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }

            item {
                SmallTitle(
                    text = stringResource(
                        if (uiState.isGeneratingPreview)
                            R.string.preview_title_generating
                        else
                            R.string.preview_title,
                        uiState.previews.size
                    )
                )
            }

            if (uiState.previews.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.preview_empty_tip),
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        uiState.previews.forEachIndexed { index, preview ->
                            PreviewItem(preview = preview)
                            if (index < uiState.previews.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let {
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
                        it.asString(context)?.let { text ->
                            Text(
                                text = text, fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        uiState.renameResult?.let { result ->
            val summary = buildString {
                    append(
                        stringResource(
                            R.string.rename_result_summary,
                            result.successCount,
                            result.totalCount
                        )
                    )
                    append("\n")

                    if (result.failureCount > 0) {
                        append(
                            stringResource(
                                R.string.rename_result_failure,
                                result.failureCount
                            )
                        )
                        append("\n")

                        result.failed.take(3).forEach { (_, error) ->
                            append("• $error\n")
                        }

                        if (result.failed.size > 3) {
                            append("...")
                        }
                    }
                }
            WindowDialog(
                show = true,
                title =  stringResource(
                    if (result.isSuccessful)
                        R.string.rename_success_title
                    else
                        R.string.rename_finished_title
                ),
                summary = summary,
                onDismissRequest = {
                    viewModel.clearResult()
                }
            ) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.confirm),
                    onClick = {
                        viewModel.clearResult()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun PreviewItem(preview: RenamePreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.label_old_name,
                preview.originalPath.substringAfterLast('/')
            ),
            style = MiuixTheme.textStyles.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = stringResource(
                R.string.label_new_name,
                preview.newPath.substringAfterLast('/')
            ),
            style = MiuixTheme.textStyles.body1.copy(if (preview.conflict) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (preview.conflict) {
            Text(
                text = stringResource(R.string.rename_conflict_warning),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PlaceholderInfoContent() {
    val placeholders = TagField.entries.map {
        "@${it.index}" to it.description
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        placeholders.forEach { (placeholder, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(description),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    }
}
