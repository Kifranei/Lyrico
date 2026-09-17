package com.lonx.lyrico.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalLyricSearchResult
import com.lonx.lyrico.data.model.search.LocalSearchUiState
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.album.AlbumListItem
import com.lonx.lyrico.ui.components.artist.ArtistListItem
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.search.SearchSectionHeader
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.utils.AdvancedSearchCondition
import com.lonx.lyrico.utils.AdvancedSearchJoinMode
import com.lonx.lyrico.utils.AdvancedSearchOperator
import com.lonx.lyrico.utils.TagTextField
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AlbumDetailDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "local_search")
fun LocalSearchScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: LocalSearchViewModel = koinViewModel()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdvancedSearchEnabled by viewModel.isAdvancedSearchEnabled.collectAsStateWithLifecycle()
    val advancedJoinMode by viewModel.advancedJoinMode.collectAsStateWithLifecycle()
    val advancedConditions by viewModel.advancedConditions.collectAsStateWithLifecycle()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsStateWithLifecycle()
    val swipeSelectionLabel = stringResource(
        if (!isSelectionMode) {
            R.string.swipe_selection_enter_selection
        } else if (swipeAnchorUri == null) {
            R.string.swipe_selection_range_start
        } else {
            R.string.swipe_selection_range_end
        }
    )
    val swipeSelectionSecondaryLabel = if (!isSelectionMode) {
        stringResource(R.string.swipe_selection_range_start)
    } else {
        null
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchTabs = LocalSearchTab.entries.filter {
        it != LocalSearchTab.Lyrics || uiState.lyricSearchEnabled
    }
    val pagerState = rememberPagerState(pageCount = { searchTabs.size })
    LaunchedEffect(searchTabs) {
        if (pagerState.currentPage >= searchTabs.size) pagerState.scrollToPage(0)
    }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val hasResults = uiState.songs.isNotEmpty() ||
        uiState.albums.isNotEmpty() ||
        uiState.artists.isNotEmpty() ||
        uiState.lyricMatches.isNotEmpty()
    val visibleSongs = visibleSongsForTab(
        tab = searchTabs.getOrElse(pagerState.currentPage) { LocalSearchTab.All },
        uiState = uiState
    )
    val searchState = rememberTextFieldState(initialText = searchQuery)

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }
            .distinctUntilChanged()
            .collectLatest(viewModel::onQueryChange)
    }

    LaunchedEffect(isSelectionMode, pagerState.currentPage) {
        if (isSelectionMode && visibleSongs.isEmpty()) {
            selectionViewModel.exitSelectionMode()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else {
            selectionViewModel.exitSelectionMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier.background(MiuixTheme.colorScheme.surface)
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        label = "LocalSearchTopBarAnimation",
                        transitionSpec = {
                            val animationDuration = 300
                            val enter = fadeIn(tween(animationDuration)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetY = { -it / 3 }
                                )
                            val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetY = { -it / 3 }
                                )

                            (enter togetherWith exit).using(SizeTransform(clip = false))
                        }
                    ) { selectionMode ->
                        if (selectionMode) {
                            SongSelectionTopAppBar(
                                songs = visibleSongs,
                                selectedSongUris = selectedSongUris,
                                scrollBehavior = topAppBarScrollBehavior,
                                onSelectAll = selectionViewModel::selectAll,
                                onDeselectAll = selectionViewModel::deselectAll,
                                onClose = selectionViewModel::exitSelectionMode
                            )
                        } else {
                            SmallTopAppBar(
                                title = stringResource(R.string.local_search_title),
                                navigationIcon = {
                                    IconButton(onClick = { navigator.popBackStack() }) {
                                        Icon(
                                            imageVector = MiuixIcons.Back,
                                            contentDescription = stringResource(R.string.action_back)
                                        )
                                    }
                                },
                                scrollBehavior = topAppBarScrollBehavior
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = expandVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            ),
                            initialOffsetY = { -it / 3 }
                        ) + fadeIn(tween(300)),
                        exit = shrinkVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            ),
                            targetOffsetY = { -it / 3 }
                        ) + fadeOut(tween(300))
                    ) {
                        Column {
                            SearchBar(
                                state = searchState,
                                placeholder = stringResource(R.string.local_search_hint),
                                onSearch = { keyword ->
                                    viewModel.onQueryChange(keyword)
                                },
                                autoFocus = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            AdvancedSearchPanel(
                                enabled = isAdvancedSearchEnabled,
                                joinMode = advancedJoinMode,
                                conditions = advancedConditions,
                                onEnabledChange = viewModel::setAdvancedSearchEnabled,
                                onJoinModeChange = viewModel::setAdvancedJoinMode,
                                onAddCondition = viewModel::addAdvancedCondition,
                                onRemoveCondition = viewModel::removeAdvancedCondition,
                                onFieldChange = viewModel::updateAdvancedConditionField,
                                onOperatorChange = viewModel::updateAdvancedConditionOperator,
                                onValueChange = viewModel::updateAdvancedConditionValue,
                                onIgnoreCaseChange = viewModel::updateAdvancedConditionIgnoreCase
                            )
                            LocalSearchPillTabRow(
                                tabs = searchTabs,
                                selectedTabIndex = pagerState.currentPage,
                                onTabSelected = { index ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LocalSearchResultsPage(
                    tab = searchTabs.getOrElse(page) { LocalSearchTab.All },
                    uiState = uiState,
                    searchQuery = searchQuery,
                    advancedSearchEnabled = isAdvancedSearchEnabled,
                    hasAnyResults = hasResults,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    contentPaddingValues = paddingValues,
                    onArtistClick = { artist ->
                        selectionViewModel.exitSelectionMode()
                        navigator.navigate(ArtistDetailDestination(artistId = artist.id))
                    },
                    onAlbumClick = { album ->
                        selectionViewModel.exitSelectionMode()
                        navigator.navigate(AlbumDetailDestination(albumId = album.id))
                    },
                    onSongClick = { song ->
                        selectionViewModel.exitSelectionMode()
                        navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                    },
                    onToggleSelection = { song ->
                        selectionViewModel.toggleSelection(song.uri)
                    },
                    onSwipeSelection = { song, pageSongs ->
                        selectionViewModel.swipeSelect(song, pageSongs)
                    },
                    onShowSongMenu = { song ->
                        selectedSong = song
                        showMenuSheet = true
                    }
                )
            }
        }

        SongBatchSelectionActions(
            navigator = navigator,
            songs = visibleSongs,
            show = isSelectionMode,
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare
        )

        SongActionSheets(
            selectedSong = selectedSong,
            showMenuSheet = showMenuSheet,
            showDetailSheet = showDetailSheet,
            showDeleteDialog = showDeleteDialog,
            showRenameDialog = showRenameDialog,
            onDismissMenu = { showMenuSheet = false },
            onDismissMenuFinished = { selectedSong = null },
            onDismissDetail = { showDetailSheet = false },
            onDismissDelete = { showDeleteDialog = false },
            onDismissRename = { showRenameDialog = false },
            onShowDetail = { showDetailSheet = true },
            onShowDelete = { showDeleteDialog = true },
            onShowRename = { showRenameDialog = true },
            onPlay = { song -> selectionViewModel.play(context, song) },
            onDelete = { song -> selectionViewModel.delete(song) },
            onRename = { song, newFileName ->
                selectionViewModel.renameSong(song, newFileName)
            }
        )
    }
}

private enum class LocalSearchTab(val labelRes: Int) {
    All(R.string.search_type_all),
    Songs(R.string.search_section_songs),
    Albums(R.string.search_section_albums),
    Artists(R.string.search_section_artists),
    Lyrics(R.string.label_lyrics)
}

@Composable
private fun LocalSearchPillTabRow(
    tabs: List<LocalSearchTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            PillButton(
                text = stringResource(tab.labelRes),
                selected = index == selectedTabIndex,
                style = PillButtonDefaults.style(PillButtonSize.Medium),
                colors = PillButtonDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ),
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun LocalSearchResultsPage(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    searchQuery: String,
    advancedSearchEnabled: Boolean,
    hasAnyResults: Boolean,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPaddingValues: PaddingValues,
    onArtistClick: (ArtistEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    val listState = rememberLazyListState()
    val pageSongs = visibleSongsForTab(tab, uiState)

    LazyColumn(
        modifier = Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        state = listState,
        contentPadding = scaffoldContentPadding(
            paddingValues = contentPaddingValues,
            bottomExtra = 12.dp
        ),
        overscrollEffect = null
    ) {
        if ((searchQuery.isNotBlank() || advancedSearchEnabled) && !hasAnyResults) {
            item {
                SearchEmptyCard()
            }
        }

        if (tab == LocalSearchTab.All || tab == LocalSearchTab.Songs) {
            SongsSection(
                songs = uiState.songs,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
        }

        if (tab == LocalSearchTab.All || tab == LocalSearchTab.Albums) {
            AlbumsSection(
                albums = uiState.albums,
                onAlbumClick = onAlbumClick
            )
        }

        if (tab == LocalSearchTab.All || tab == LocalSearchTab.Artists) {
            ArtistsSection(
                artists = uiState.artists,
                onArtistClick = onArtistClick
            )
        }

        if (uiState.lyricSearchEnabled && (tab == LocalSearchTab.All || tab == LocalSearchTab.Lyrics)) {
            LyricsSection(
                matches = uiState.lyricMatches,
                query = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
        }
    }
}

private fun visibleSongsForTab(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState
): List<SongEntity> {
    return when (tab) {
        LocalSearchTab.All -> (uiState.songs + uiState.lyricMatches.map { it.song })
            .distinctBy { it.uri }
        LocalSearchTab.Songs -> uiState.songs
        LocalSearchTab.Lyrics -> uiState.lyricMatches.map { it.song }
        LocalSearchTab.Albums,
        LocalSearchTab.Artists -> emptyList()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.SongsSection(
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (songs.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.search_section_songs),
            subtitle = stringResource(R.string.song_count, songs.size)
        )
    }
    items(
        items = songs,
        key = ::localSearchSongKey
    ) { song ->
        LocalSearchSongItem(
            song = song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            lyricPreview = null,
            lyricMatchQuery = null,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.LyricsSection(
    matches: List<LocalLyricSearchResult>,
    query: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (matches.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.label_lyrics),
            subtitle = stringResource(R.string.song_count, matches.size)
        )
    }
    items(
        items = matches,
        key = { match -> "local-search-lyric-${localSearchSongKey(match.song)}" }
    ) { match ->
        LocalSearchSongItem(
            song = match.song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(match.song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            lyricPreview = match.lyricLine,
            lyricMatchQuery = query,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.AlbumsSection(
    albums: List<AlbumEntity>,
    onAlbumClick: (AlbumEntity) -> Unit
) {
    if (albums.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.search_section_albums),
            subtitle = stringResource(R.string.album_count, albums.size)
        )
    }
    items(
        items = albums,
        key = { album -> "${album.name}|${album.albumArtist.orEmpty()}" }
    ) { album ->
        AlbumListItem(
            album = album,
            onClick = { onAlbumClick(album) }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ArtistsSection(
    artists: List<ArtistEntity>,
    onArtistClick: (ArtistEntity) -> Unit
) {
    if (artists.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.search_section_artists),
            subtitle = stringResource(R.string.song_count, artists.size)
        )
    }
    items(
        items = artists,
        key = { artist -> artist.name }
    ) { artist ->
        ArtistListItem(
            artist = artist,
            onClick = { onArtistClick(artist) }
        )
    }
}

@Composable
private fun LocalSearchSongItem(
    song: SongEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    lyricPreview: String?,
    lyricMatchQuery: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    SongListItem(
        song = song,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        swipeSelectionLabel = swipeSelectionLabel,
        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
        lyricPreview = lyricPreview,
        lyricMatchQuery = lyricMatchQuery,
        onClick = { onSongClick(song) },
        onToggleSelection = { onToggleSelection(song) },
        onSwipeSelection = { onSwipeSelection(song, pageSongs) },
        trailingContent = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                SongListItemActions(
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onToggleSelection = { onToggleSelection(song) },
                    onShowMenu = { onShowSongMenu(song) }
                )
            }
        }
    )
}

@Composable
private fun AdvancedSearchPanel(
    enabled: Boolean,
    joinMode: AdvancedSearchJoinMode,
    conditions: List<AdvancedSearchCondition>,
    onEnabledChange: (Boolean) -> Unit,
    onJoinModeChange: (AdvancedSearchJoinMode) -> Unit,
    onAddCondition: () -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onFieldChange: (Int, TagTextField) -> Unit,
    onOperatorChange: (Int, AdvancedSearchOperator) -> Unit,
    onValueChange: (Int, String) -> Unit,
    onIgnoreCaseChange: (Int, Boolean) -> Unit
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            SwitchPreference(
                title = stringResource(R.string.advanced_search_title),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                insideMargin = PaddingValues(horizontal = 12.dp)
            )

            if (enabled) {
                BasicComponent(
                    insideMargin = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        onJoinModeChange(
                            if (joinMode == AdvancedSearchJoinMode.AND) {
                                AdvancedSearchJoinMode.OR
                            } else {
                                AdvancedSearchJoinMode.AND
                            }
                        )
                    },
                    endActions = {
                        Text(text = stringResource(joinMode.labelRes()))
                    }
                ) {
                    Text(text = stringResource(R.string.advanced_search_join_mode))
                }

                conditions.forEachIndexed { index, condition ->
                    AdvancedSearchConditionEditor(
                        index = index,
                        condition = condition,
                        canRemove = conditions.size > 1,
                        onRemoveCondition = onRemoveCondition,
                        onFieldChange = onFieldChange,
                        onOperatorChange = onOperatorChange,
                        onValueChange = onValueChange,
                        onIgnoreCaseChange = onIgnoreCaseChange
                    )
                }

                TextButton(
                    text = stringResource(R.string.advanced_search_add_condition),
                    onClick = onAddCondition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AdvancedSearchConditionEditor(
    index: Int,
    condition: AdvancedSearchCondition,
    canRemove: Boolean,
    onRemoveCondition: (Int) -> Unit,
    onFieldChange: (Int, TagTextField) -> Unit,
    onOperatorChange: (Int, AdvancedSearchOperator) -> Unit,
    onValueChange: (Int, String) -> Unit,
    onIgnoreCaseChange: (Int, Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(0.dp),
                onClick = {
                    onFieldChange(index, condition.field.next())
                }
            ) {
                Text(text = stringResource(condition.field.labelRes))
            }
            Spacer(modifier = Modifier.width(8.dp))
            BasicComponent(
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(0.dp),
                onClick = {
                    onOperatorChange(index, condition.operator.next())
                }
            ) {
                Text(text = stringResource(condition.operator.labelRes()))
            }
        }

        if (condition.operator.requiresValue()) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                value = condition.value,
                onValueChange = { onValueChange(index, it) },
                label = stringResource(R.string.advanced_search_value)
            )
        }

        SwitchPreference(
            title = stringResource(R.string.advanced_search_ignore_case),
            checked = condition.ignoreCase,
            enabled = condition.operator.requiresValue(),
            onCheckedChange = { onIgnoreCaseChange(index, it) },
            insideMargin = PaddingValues(0.dp)
        )

        if (canRemove) {
            TextButton(
                text = stringResource(R.string.advanced_search_remove_condition),
                onClick = { onRemoveCondition(index) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun TagTextField.next(): TagTextField {
    val entries = TagTextField.entries
    return entries[(entries.indexOf(this) + 1) % entries.size]
}

private fun AdvancedSearchOperator.next(): AdvancedSearchOperator {
    val entries = AdvancedSearchOperator.entries
    return entries[(entries.indexOf(this) + 1) % entries.size]
}

private fun AdvancedSearchOperator.requiresValue(): Boolean {
    return this != AdvancedSearchOperator.IS_EMPTY && this != AdvancedSearchOperator.IS_NOT_EMPTY
}

private fun AdvancedSearchJoinMode.labelRes(): Int {
    return when (this) {
        AdvancedSearchJoinMode.AND -> R.string.advanced_search_join_and
        AdvancedSearchJoinMode.OR -> R.string.advanced_search_join_or
    }
}

private fun AdvancedSearchOperator.labelRes(): Int {
    return when (this) {
        AdvancedSearchOperator.IS_EMPTY -> R.string.advanced_search_operator_empty
        AdvancedSearchOperator.IS_NOT_EMPTY -> R.string.advanced_search_operator_not_empty
        AdvancedSearchOperator.EQUALS -> R.string.advanced_search_operator_equals
        AdvancedSearchOperator.NOT_EQUALS -> R.string.advanced_search_operator_not_equals
        AdvancedSearchOperator.CONTAINS -> R.string.advanced_search_operator_contains
        AdvancedSearchOperator.NOT_CONTAINS -> R.string.advanced_search_operator_not_contains
        AdvancedSearchOperator.REGEX -> R.string.advanced_search_operator_regex
    }
}

@Composable
private fun SearchEmptyCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            BasicComponent(
                title = stringResource(R.string.search_empty)
            )
        }
    }
}

private fun localSearchSongKey(song: SongEntity): String {
    val stableId = song.uri.takeIf { it.isNotBlank() && it != "0" } ?: song.id.toString()
    return "local-search-song-$stableId"
}
