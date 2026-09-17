package com.lonx.lyrico.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.FolderManagementItem
import com.lonx.lyrico.ui.components.library.LibraryBlurredBar
import com.lonx.lyrico.ui.components.library.rememberBarBlurEnabled
import com.lonx.lyrico.ui.components.library.rememberBlurBackdrop
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.viewmodel.ArtistPosterFolder
import com.lonx.lyrico.viewmodel.ArtistPosterFoldersUiState
import com.lonx.lyrico.viewmodel.ArtistPosterFoldersViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@Destination<RootGraph>(route = "artist_poster_folders")
fun ArtistPosterFoldersScreen(navigator: DestinationsNavigator) {
    val viewModel: ArtistPosterFoldersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            viewModel.addFolder(it)
        }
    }
    var selectedFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFolder = state.folders.find { it.uri == selectedFolderUri }
    var currentFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val currentFolder = state.folders.find { it.uri == currentFolderUri }
    BackHandler(currentFolder != null) { currentFolderUri = null }
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBlurBackdrop(enableBlur = rememberBarBlurEnabled())

    Scaffold(
        topBar = {
            LibraryBlurredBar(
                backdrop = topBarBackdrop,
                modifier = Modifier.scaffoldTopAppBarInsetsPadding()
            ) {
                SmallTopAppBar(
                    title = currentFolder?.name ?: stringResource(R.string.artist_poster_folders),
                    color = if (topBarBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = { if (currentFolder != null) currentFolderUri = null else navigator.navigateUp() }) {
                            Icon(MiuixIcons.Back, stringResource(R.string.artist_poster_back))
                        }
                    },
                    actions = {
                        if (currentFolder == null) {
                            IconButton(onClick = { picker.launch(null) }) {
                                Icon(MiuixIcons.AddFolder, stringResource(R.string.artist_poster_folder_add))
                            }
                        }
                        OverlayIconDropdownMenu(entry = DropdownEntry(items = listOf(
                            DropdownItem(text = stringResource(R.string.action_refresh_folder), onClick = viewModel::refresh),
                            DropdownItem(text = stringResource(R.string.artist_poster_naming), onClick = { showHelp = true })
                        ))) {
                            Icon(MiuixIcons.More, stringResource(R.string.cd_more_actions))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        // Opening a folder slides like every other page of the app, back reverses it.
        AnimatedContent(
            targetState = currentFolder,
            label = "ArtistPosterFolderContent",
            transitionSpec = {
                val openFolder = targetState != null
                val animation = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
                val enter = slideInHorizontally(animationSpec = animation) { if (openFolder) it else -it }
                val exit = slideOutHorizontally(animationSpec = animation) { if (openFolder) -it else it }
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            modifier = Modifier
                .fillMaxSize()
                .then(if (topBarBackdrop != null) Modifier.layerBackdrop(topBarBackdrop) else Modifier)
        ) { folder ->
            if (folder != null) {
                ArtistPosterFolderContents(
                    folder = folder,
                    revision = state.revision,
                    isLoading = state.isLoading,
                    padding = scaffoldContentPadding(padding),
                    scrollBehavior = scrollBehavior
                )
            } else {
                ArtistPosterFolderList(
                    state = state,
                    onAddFolder = { picker.launch(null) },
                    onOpenFolder = { currentFolderUri = it.uri },
                    onRemoveFolder = { selectedFolderUri = it.uri },
                    onRefresh = viewModel::refresh,
                    padding = scaffoldContentPadding(padding),
                    scrollBehavior = scrollBehavior
                )
            }
        }
        WindowDialog(
            title = stringResource(R.string.artist_poster_naming),
            show = showHelp,
            onDismissRequest = { showHelp = false }
        ) {
            MarkdownText(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                markdown = stringResource(R.string.artist_poster_folders_help),
                linkColor = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body2.copy(
                    color = MiuixTheme.colorScheme.onSurface
                )
            )
        }
        if (selectedFolder != null) {
            WindowDialog(
                title = stringResource(R.string.dialog_remove_folder_title),
                show = true,
                onDismissRequest = { selectedFolderUri = null }
            ) {
                Column {
                    Text(selectedFolder.path, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.artist_poster_folder_remove_tip),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { selectedFolderUri = null },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                viewModel.removeFolder(selectedFolder.uri)
                                selectedFolderUri = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPosterFolderList(
    state: ArtistPosterFoldersUiState,
    onAddFolder: () -> Unit,
    onOpenFolder: (ArtistPosterFolder) -> Unit,
    onRemoveFolder: (ArtistPosterFolder) -> Unit,
    onRefresh: () -> Unit,
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = padding,
        overscrollEffect = null
    ) {
        item {
            Text(
                stringResource(R.string.artist_poster_folders_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }
        if (state.error) {
            item {
                Text(
                    stringResource(R.string.artist_poster_folder_error),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (state.isLoading && state.folders.isEmpty()) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(12.dp)) }
        } else if (state.folders.isEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.artist_poster_folder_add),
                        summary = stringResource(R.string.artist_poster_folders_empty),
                        onClick = onAddFolder
                    )
                }
            }
        }
        items(state.folders, key = { it.uri }) { folder ->
            FolderManagementItem(
                name = folder.name,
                path = folder.path,
                status = when {
                    state.isLoading -> stringResource(R.string.folder_scanning)
                    folder.posters == null -> stringResource(R.string.artist_poster_folder_unavailable)
                    else -> stringResource(
                        R.string.artist_poster_folder_status,
                        folder.posters.size,
                        folder.matchedPosterCount
                    )
                },
                actions = DropdownEntry(items = listOf(
                    DropdownItem(text = stringResource(R.string.action_refresh_folder), onClick = onRefresh),
                    DropdownItem(text = stringResource(R.string.folder_action_remove), onClick = { onRemoveFolder(folder) })
                )),
                enabled = !state.isLoading,
                isLoading = state.isLoading,
                onClick = { onOpenFolder(folder) }
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
