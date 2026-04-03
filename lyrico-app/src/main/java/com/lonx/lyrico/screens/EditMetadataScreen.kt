package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic


@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
@Destination<RootGraph>(route = "edit_metadata")
fun EditMetadataScreen(
    navigator: DestinationsNavigator,
    songFileUri: String,
    onLyricsResult: ResultRecipient<SearchResultsDestination, LyricsSearchResult>
) {
    val viewModel: EditMetadataViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData


    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity
    // BottomSheet 状态
    var showOffsetSheet by remember { mutableStateOf(false) }
    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    val currentShiftOffset by viewModel.currentShiftOffset.collectAsState()

    // 各种 Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.updateCover(it) } }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.saveMetadata()
        else scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.permission_denied_cannot_save)) }
    }

    // 事件监听
    onLyricsResult.onResult { result -> viewModel.updateMetadataFromSearchResult(result) }

    LaunchedEffect(uiState.permissionIntentSender) {
        uiState.permissionIntentSender?.let { intentSender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            viewModel.consumePermissionRequest()
        }
    }

    LaunchedEffect(songFileUri) { viewModel.readMetadata(songFileUri) }

    LaunchedEffect(uiState.saveSuccess) {
        uiState.saveSuccess?.let { success ->
            val msg = if (success) R.string.msg_save_success else R.string.msg_save_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearSaveStatus()
            if (success) {
                if (!navigator.popBackStack()){
                    activity.finish()
                }
            }
        }
    }

    BackHandler {
        if (!navigator.popBackStack()) {
            activity.finish()
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val titleText = uiState.songInfo?.tagData?.title
                ?: uiState.songInfo?.tagData?.fileName
                ?: stringResource(R.string.edit_metadata_default_title)

            SmallTopAppBar(
                title = titleText,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = {
                            if (!navigator.popBackStack()) {
                                activity.finish()
                            }
                        }
                    ) { Icon(imageVector = MiuixIcons.Back, contentDescription = null) }
                },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = {
                        val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                            if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                            else "${editingTagData.title} ${editingTagData.artist}"
                        } else {
                            uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                        }
                        navigator.navigate(SearchResultsDestination(keyword))
                    }) { Icon(imageVector = MiuixIcons.Search, contentDescription = null) }

                    // 保存按钮
                    IconButton(
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = { viewModel.saveMetadata() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Icon(imageVector = MiuixIcons.Ok, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!editingTagData?.lyrics.isNullOrBlank()) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.prepareLyricsOffset()
                            showOffsetSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Tune,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { viewModel.play(context) }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }

        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .scrollEndHaptic()
                .imePadding(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp, // 留出空间给 FAB
            )
        ) {
            // 封面编辑区
            item(key = "cover") {
                Card(
                    modifier = Modifier.padding(12.dp)
                ) {
                    CoverEditor(
                        coverUri = uiState.coverUri,
                        isModified = uiState.coverUri != uiState.originalCover,
                        onCoverClick = { showCoverOptionsSheet = true },
                        onRevertClick = { viewModel.revertCover() }
                    )
                }
            }

            // 基础信息组
            item(key = "title") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_title),
                    value = editingTagData?.title ?: "",
                    onValueChange = { viewModel.updateTag { copy(title = it) } },
                    isModified = editingTagData?.title != originalTagData?.title,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                title = originalTagData?.title ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "artist") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_artists),
                    value = editingTagData?.artist ?: "",
                    onValueChange = { viewModel.updateTag { copy(artist = it) } },
                    isModified = editingTagData?.artist != originalTagData?.artist,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                artist = originalTagData?.artist ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "album_artist") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_album_artist),
                    value = editingTagData?.albumArtist ?: "",
                    onValueChange = { viewModel.updateTag { copy(albumArtist = it) } },
                    isModified = editingTagData?.albumArtist != originalTagData?.albumArtist,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                albumArtist = originalTagData?.albumArtist ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "album") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_album),
                    value = editingTagData?.album ?: "",
                    onValueChange = { viewModel.updateTag { copy(album = it) } },
                    isModified = editingTagData?.album != originalTagData?.album,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                album = originalTagData?.album ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "date") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_date),
                    value = editingTagData?.date ?: "",
                    onValueChange = { viewModel.updateTag { copy(date = it) } },
                    isModified = editingTagData?.date != originalTagData?.date,
                    onRevert = { viewModel.updateTag { copy(date = originalTagData?.date ?: "") } }
                )
            }

            item(key = "genre") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_genre),
                    value = editingTagData?.genre ?: "",
                    onValueChange = { viewModel.updateTag { copy(genre = it) } },
                    isModified = editingTagData?.genre != originalTagData?.genre,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                genre = originalTagData?.genre ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "track_number") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_track_number),
                    value = editingTagData?.trackNumber ?: "",
                    onValueChange = { viewModel.updateTag { copy(trackNumber = it) } },
                    isModified = editingTagData?.trackNumber != originalTagData?.trackNumber,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                trackNumber = originalTagData?.trackNumber ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "disc_number") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_disc_number),
                    value = editingTagData?.discNumber?.toString() ?: "",
                    onValueChange = { viewModel.updateTag { copy(discNumber = it.toIntOrNull()) } },
                    isModified = editingTagData?.discNumber != originalTagData?.discNumber,
                    onRevert = { viewModel.updateTag { copy(discNumber = originalTagData?.discNumber) } }
                )
            }
            item(key = "composer") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_composer),
                    value = editingTagData?.composer ?: "",
                    onValueChange = { viewModel.updateTag { copy(composer = it) } },
                    isModified = editingTagData?.composer != originalTagData?.composer,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                composer = originalTagData?.composer ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "lyricist") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_lyricist),
                    value = editingTagData?.lyricist ?: "",
                    onValueChange = { viewModel.updateTag { copy(lyricist = it) } },
                    isModified = editingTagData?.lyricist != originalTagData?.lyricist,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                lyricist = originalTagData?.lyricist ?: ""
                            )
                        }
                    }
                )
            }

            // 其他信息
            item(key = "commit") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_comment),
                    value = editingTagData?.comment ?: "",
                    onValueChange = { viewModel.updateTag { copy(comment = it) } },
                    isModified = editingTagData?.comment != originalTagData?.comment,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                comment = originalTagData?.comment ?: ""
                            )
                        }
                    }
                )
            }

            item(key = "lyrics") {
                MetadataInputGroup(
                    label = stringResource(R.string.label_lyrics),
                    value = editingTagData?.lyrics ?: "",
                    onValueChange = { viewModel.updateTag { copy(lyrics = it) } },
                    isModified = editingTagData?.lyrics != originalTagData?.lyrics,
                    onRevert = {
                        viewModel.updateTag {
                            copy(
                                lyrics = originalTagData?.lyrics ?: ""
                            )
                        }
                    },
                    actionButtons = {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary)
                                .clickable {
                                    viewModel.prepareLyricsOffset()
                                    showOffsetSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.offset_adjust_hint),
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    isMultiline = true
                )
            }
        }
    }

    // 封面操作
    SuperBottomSheet(
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
            item(key = "cover_options") {
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    SuperArrow(
                        title = stringResource(R.string.label_change_cover),
                        onClick = {
                            showCoverOptionsSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.label_remove_cover),
                        onClick = {
                            showCoverOptionsSheet = false
                            viewModel.removeCover()
                        }
                    )
                    if (uiState.coverUri != null || uiState.originalCover != null) {
                        SuperArrow(
                            title = stringResource(R.string.label_save_cover),
                            onClick = {
                                showCoverOptionsSheet = false
                                viewModel.exportCover(context)
                            }
                        )
                    }
                }
            }
        }
    }

    // 偏移调整 BottomSheet
    SuperBottomSheet(
        show = showOffsetSheet,
        onDismissRequest = { showOffsetSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = editingTagData?.lyrics ?: "",
                        style = MiuixTheme.textStyles.footnote1
                    )
                }
            }

            OffsetAdjustPanel(
                currentOffset = currentShiftOffset,
                onOffsetChange = { viewModel.applyLyricsOffset(it) },
                onReset = { viewModel.resetLyricsOffset() }
            )
        }
    }
}

@Composable
fun CoverEditor(
    modifier: Modifier = Modifier,
    coverUri: Any?,
    isModified: Boolean = false,
    onCoverClick: () -> Unit,
    onRevertClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onCoverClick() }
    ) {
        AsyncImage(
            model = coverUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
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

        if (isModified) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.9f))
                        .clickable { onRevertClick() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.status_modified),
                        fontSize = 11.sp,
                        color = LyricoColors.modifiedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isModified: Boolean = false,
    onRevert: () -> Unit,
    isMultiline: Boolean = false,
    actionButtons: @Composable RowScope.() -> Unit = {}
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTitle(text = label, insideMargin = PaddingValues(0.dp))

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isModified) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = stringResource(R.string.status_modified),
                            fontSize = 11.sp,
                            color = LyricoColors.modifiedText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                actionButtons()
            }
        }

        TextField(
            textStyle = MiuixTheme.textStyles.body2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            value = value,
            onValueChange = onValueChange,
            borderColor = if (isModified) LyricoColors.modifiedBorder else MiuixTheme.colorScheme.primary,
            singleLine = !isMultiline,
            minLines = if (isMultiline) 10 else 1,
        )
    }
}