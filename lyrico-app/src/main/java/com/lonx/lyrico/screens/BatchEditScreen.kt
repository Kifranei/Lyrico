package com.lonx.lyrico.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.BatchEditField
import com.lonx.lyrico.viewmodel.BatchEditViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.core.net.toUri


@Composable
@Destination<RootGraph>(route = "batch_edit")
fun BatchEditScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchEditViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.updateCover(it) } }

    val scope = rememberCoroutineScope()

    // 加载同专辑封面（直接使用第一张）
    fun loadSameAlbumCovers() {
        scope.launch {
            try {
                val covers = viewModel.getSameAlbumCovers()
                if (covers.isNotEmpty()) {
                    val (_, cover) = covers.first()
                    when (cover) {
                        is String -> {
                            viewModel.updateCover(cover.toUri())
                        }

                        is ByteArray -> {
                            // 将 ByteArray 转换为 Bitmap，然后保存为临时文件
                            val bitmap =
                                android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size)
                            val tempFile = java.io.File.createTempFile("cover", ".jpg")
                            tempFile.outputStream().use {
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.JPEG,
                                    100,
                                    it
                                )
                            }
                            viewModel.updateCover(android.net.Uri.fromFile(tempFile))
                        }
                    }
                }
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.batch_edit_title),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!uiState.isSaving) navigator.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInfoDialog = true }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = { viewModel.saveBatchEdit() },
                        enabled = !uiState.isSaving
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
        floatingToolbarPosition = ToolbarPosition.CenterEnd,
        floatingToolbar = {
            FloatingToolbar() {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            showAddCustomTagDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight()
                .imePadding(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp,
            ),
            overscrollEffect = null,
        ) {
            // 歌曲数量信息
            item(key = "song_count") {
                SmallTitle(
                    text = stringResource(R.string.batch_edit_song_count, uiState.songCount)
                )
            }

            // 封面编辑区
            item(key = "cover_editor") {
                BatchEditCoverSection(
                    coverUri = uiState.coverUri,
                    isRemoved = uiState.removeCover,
                    onCoverClick = { showCoverOptionsSheet = true },
                    onRemoveClick = { viewModel.removeCover() },
                    onRevertClick = { viewModel.revertCover() }
                )
            }
            // 评分组
            item(key = "rating") {
                Column {
                    SmallTitle(text = stringResource(R.string.label_rating))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        BatchEditRatingItem(
                            rating = uiState.rating,
                            isModified = uiState.ratingModified,
                            onRatingChange = { viewModel.updateRating(it) },
                            onRevert = { viewModel.resetRating() }
                        )
                    }
                }
            }
            // 基础信息组
            item(key = "basic_info") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_basic_info))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            BatchEditFieldItem(
                                field = BatchEditField.TITLE,
                                value = uiState.title,
                                onValueChange = { viewModel.updateTitle(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.ARTIST,
                                value = uiState.artist,
                                onValueChange = { viewModel.updateArtist(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.ALBUM_ARTIST,
                                value = uiState.albumArtist,
                                onValueChange = { viewModel.updateAlbumArtist(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.ALBUM,
                                value = uiState.album,
                                onValueChange = { viewModel.updateAlbum(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.DATE,
                                value = uiState.date,
                                onValueChange = { viewModel.updateDate(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.GENRE,
                                value = uiState.genre,
                                onValueChange = { viewModel.updateGenre(it) }
                            )
                        }
                    }
                }
            }

            // 曲目详情组
            item(key = "track_details") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_track_details))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            BatchEditFieldItem(
                                field = BatchEditField.TRACK_NUMBER,
                                value = uiState.trackNumber,
                                onValueChange = { viewModel.updateTrackNumber(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.DISC_NUMBER,
                                value = uiState.discNumber,
                                onValueChange = { viewModel.updateDiscNumber(it) }
                            )
                        }
                    }
                }
            }

            // 制作人员和其他信息组
            item(key = "credits_other") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_credits_other))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            BatchEditFieldItem(
                                field = BatchEditField.COMPOSER,
                                value = uiState.composer,
                                onValueChange = { viewModel.updateComposer(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.LYRICIST,
                                value = uiState.lyricist,
                                onValueChange = { viewModel.updateLyricist(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.COPYRIGHT,
                                value = uiState.copyright,
                                onValueChange = { viewModel.updateCopyright(it) }
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.COMMENT,
                                value = uiState.comment,
                                onValueChange = { viewModel.updateComment(it) }
                            )
                        }
                    }
                }
            }


            // 自定义标签组
            if (uiState.customFields.isNotEmpty()) {
                item(key = "custom_fields") {
                    SmallTitle(text = stringResource(R.string.group_custom_tags))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            uiState.customFields.forEachIndexed { index, field ->
                                BatchEditCustomFieldItem(
                                    field = field,
                                    onKeyChange = { newKey ->
                                        viewModel.updateCustomField(index, newKey, field.value)
                                    },
                                    onValueChange = { newValue ->
                                        viewModel.updateCustomField(index, field.key, newValue)
                                    },
                                    onRemove = { viewModel.removeCustomField(index) }
                                )
                            }
                        }
                    }
                }
            }
            // 歌词组
            item(key = "lyrics") {
                Column {
                    SmallTitle(text = stringResource(R.string.label_lyrics))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                value = uiState.lyricsOffset,
                                onValueChange = { viewModel.updateLyricsOffset(it) },
                                label = stringResource(R.string.label_lyrics_offset),
                            )
                            Text(
                                text = stringResource(R.string.batch_edit_lyrics_offset_hint),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                            )
                            BatchEditFieldItem(
                                field = BatchEditField.LYRICS,
                                value = uiState.lyrics,
                                onValueChange = { viewModel.updateLyrics(it) },
                                isMultiline = true
                            )
                        }
                    }
                }
            }
        }
    }

    // 封面操作 BottomSheet
    WindowBottomSheet(
        show = showCoverOptionsSheet,
        onDismissRequest = { showCoverOptionsSheet = false }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .scrollEndHaptic()
                .overScrollVertical(),
            overscrollEffect = null
        ) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.batch_edit_cover_change),
                        onClick = {
                            showCoverOptionsSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    ArrowPreference(
                        title = "选择同专辑歌曲封面",
                        onClick = {
                            showCoverOptionsSheet = false
                            loadSameAlbumCovers()
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.batch_edit_cover_remove),
                        onClick = {
                            showCoverOptionsSheet = false
                            viewModel.removeCover()
                        }
                    )
                    if (uiState.coverUri != null || uiState.removeCover) {
                        ArrowPreference(
                            title = stringResource(R.string.batch_edit_cover_revert),
                            onClick = {
                                showCoverOptionsSheet = false
                                viewModel.revertCover()
                            }
                        )
                    }
                }
            }
        }
    }

    // 批量编辑说明对话框
    WindowDialog(
        show = showInfoDialog,
        title = stringResource(R.string.batch_edit_info_summary),
        onDismissRequest = { showInfoDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_edit_info_content),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.confirm),
                onClick = { showInfoDialog = false },
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }

    // 保存进度对话框
    WindowBottomSheet(
        show = uiState.saveProgressDialog,
        onDismissRequest = {
            if (!uiState.isSaving) viewModel.closeSaveBottomSheet()
        },
        onDismissFinished = {
            if (uiState.saveSuccess == true) {
                navigator.popBackStack()
            }
        },
        allowDismiss = !uiState.isSaving,
        title = stringResource(R.string.batch_edit_title),
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (uiState.isSaving) {
                        uiState.currentFile
                    } else {
                        stringResource(
                            R.string.batch_matching_total_time,
                            uiState.saveTimeMillis / 1000.0
                        )
                    },
                    style = MiuixTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.saveProgress} / ${uiState.saveTotal}",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )
                }

                LinearProgressIndicator(
                    progress = if (uiState.saveTotal > 0)
                        uiState.saveProgress.toFloat() / uiState.saveTotal
                    else 0f
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_matching_success,
                        uiState.successCount
                    ),
                    style = MiuixTheme.textStyles.main
                )
                Text(
                    text = stringResource(
                        R.string.batch_matching_failure,
                        uiState.failureCount
                    ),
                    style = MiuixTheme.textStyles.main
                )
            }

            TextButton(
                text = if (uiState.isSaving) stringResource(R.string.action_abort) else stringResource(
                    R.string.confirm
                ),
                onClick = {
                    if (uiState.isSaving) {
                        viewModel.abortSave()
                    } else {
                        viewModel.closeSaveBottomSheet()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )

        }
    }

    // 添加自定义标签 BottomSheet
    WindowDialog(
        show = showAddCustomTagDialog,
        title = stringResource(R.string.action_add_custom_tag),
        onDismissRequest = { showAddCustomTagDialog = false }
    ) {
        // 临时存储新自定义标签内容
        var newCustomTagKey by remember { mutableStateOf("") }
        var newCustomTagValue by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {

            TextField(
                value = newCustomTagKey,
                onValueChange = { newCustomTagKey = it },
                label = stringResource(R.string.label_custom_tag_name),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = newCustomTagValue,
                onValueChange = { newCustomTagValue = it },
                label = stringResource(R.string.label_custom_tag_value),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showAddCustomTagDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        if (newCustomTagKey.isNotBlank()) {
                            viewModel.addCustomField(
                                CustomTagField(
                                    newCustomTagKey,
                                    newCustomTagValue
                                )
                            )
                            showAddCustomTagDialog = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun BatchEditRatingItem(
    rating: Int,
    isModified: Boolean,
    onRatingChange: (Int) -> Unit,
    onRevert: () -> Unit
) {
    val onSurfaceDim = MiuixTheme.colorScheme.onSurfaceVariantActions

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            endActions = {
                if (isModified) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            content = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isFilled = i <= rating

                        Icon(
                            painter = painterResource(
                                if (isFilled)
                                    R.drawable.ic_filled_star_24dp
                                else
                                    R.drawable.ic_outline_star_24dp
                            ),
                            contentDescription = null,
                            tint = if (isFilled)
                                MiuixTheme.colorScheme.primary
                            else
                                onSurfaceDim.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onRatingChange(
                                        if (rating == i) 0 else i
                                    )
                                }
                        )
                    }
                }
            }
        )
    }
}

/**
 * 封面编辑区
 */
@Composable
private fun BatchEditCoverSection(
    coverUri: Any?,
    isRemoved: Boolean,
    onCoverClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRevertClick: () -> Unit
) {
    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable { onCoverClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isRemoved) {
                    Text(
                        text = stringResource(R.string.batch_edit_cover_remove),
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                } else if (coverUri != null) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = rememberTintedPainter(
                            painter = painterResource(id = R.drawable.ic_album_24dp),
                            tint = LyricoColors.coverPlaceholderIcon
                        ),
                        error = rememberTintedPainter(
                            painter = painterResource(id = R.drawable.ic_album_24dp),
                            tint = LyricoColors.coverPlaceholderIcon
                        )
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_album_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.batch_edit_cover_hint),
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}


/**
 * 单个标签编辑字段
 * value 为 "<keep>" 时表示不修改该字段
 */
@Composable
private fun BatchEditFieldItem(
    field: BatchEditField,
    value: String,
    onValueChange: (String) -> Unit,
    isMultiline: Boolean = false
) {
    val isKeep = value == "<keep>"

    if (isMultiline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isKeep) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { /* 无法恢复，已经是 <keep> */ }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onValueChange("<keep>") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            TextField(
                textStyle = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth(),
                value = value,
                label = stringResource(field.labelResId) + if (isKeep) " (无修改)" else "",
                onValueChange = onValueChange,
                singleLine = false,
                minLines = 6
            )
        }
    } else {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            value = value,
            onValueChange = onValueChange,
            label = stringResource(field.labelResId) + if (isKeep) " (无修改)" else "",
            trailingIcon = {
                IconButton(onClick = {
                    onValueChange(if (isKeep) "" else "<keep>")
                }) {
                    Icon(
                        imageVector = if (isKeep) MiuixIcons.Close else MiuixIcons.Undo,
                        contentDescription = null,
                        tint = if (isKeep)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

/**
 * 自定义标签编辑字段
 */
@Composable
private fun BatchEditCustomFieldItem(
    field: CustomTagField,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_custom_tag),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = stringResource(R.string.action_remove_custom_tag)
                )
            }
        }

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            value = field.key,
            onValueChange = onKeyChange,
            label = stringResource(R.string.label_custom_tag_name)
        )
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            value = field.value,
            onValueChange = onValueChange,
            label = stringResource(R.string.label_custom_tag_value)
        )
    }
}

