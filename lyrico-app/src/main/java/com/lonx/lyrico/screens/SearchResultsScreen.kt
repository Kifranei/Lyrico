package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.LyricsUiState
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrics.model.SongSearchResult
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import org.koin.androidx.compose.koinViewModel

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_results")
fun SearchResultsScreen(
    keyword: String?,
    resultNavigator: ResultBackNavigator<LyricsSearchResult>
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * 外部传入 keyword 时，触发一次搜索
     */
    LaunchedEffect(keyword) {
        keyword?.let {
            viewModel.performSearch(it)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .background(SaltTheme.colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                SearchBar(
                    value = uiState.searchKeyword,
                    onValueChange = viewModel::onKeywordChanged,
                    placeholder = stringResource(id = R.string.search_lyrics_placeholder),
                    modifier = Modifier.weight(1f),
                    onSearch = {
                        viewModel.performSearch()
                        keyboardController?.hide()
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(id = R.string.action_search),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaltTheme.colors.highlight,
                    modifier = Modifier.clickable {
                        viewModel.performSearch()
                        keyboardController?.hide()
                    }
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
        ) {

            /**
             * 搜索源选择
             */
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.availableSources) { source ->
                    val isSelected = source == uiState.selectedSearchSource

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSearchSourceSelected(source) },
                        label = {
                            Text(
                                source.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SaltTheme.colors.subBackground,
                            selectedContainerColor = SaltTheme.colors.highlight.copy(alpha = 0.1f),
                            labelColor = SaltTheme.colors.text,
                            selectedLabelColor = SaltTheme.colors.highlight
                        ),
                        border = null
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = SaltTheme.colors.stroke
            )

            /**
             * 搜索结果区域
             */
            Box(modifier = Modifier.fillMaxSize()) {

                when {
                    uiState.isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SaltTheme.colors.highlight
                            )
                        }
                    }

                    uiState.searchError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.search_failed,uiState.searchError!!),
                                color = SaltTheme.colors.highlight,
                                fontSize = 14.sp
                            )
                        }
                    }

                    uiState.searchResults.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_searchoff_24dp),
                                contentDescription = stringResource(id = R.string.cd_no_results),
                                modifier = Modifier.size(48.dp),
                                tint = SaltTheme.colors.subText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = R.string.search_no_results),
                                color = SaltTheme.colors.subText
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.searchResults,
                                key = { "${it.source}_${it.id}" },
                                contentType = { "song_result" }
                            ) { result ->
                                SearchResultItem(
                                    song = result,
                                    onPreviewClick = { offset ->
                                        viewModel.loadLyrics(result, offset)
                                    },
                                    onApplyClick = { offset ->
                                        scope.launch {
                                            val lyrics = viewModel.fetchLyrics(result, offset) // 关键：传入 offset
                                            if (lyrics != null) {
                                                resultNavigator.navigateBack(
                                                    LyricsSearchResult(
                                                        title = result.title,
                                                        artist = result.artist,
                                                        album = result.album,
                                                        lyrics = lyrics,
                                                        date = result.date,
                                                        trackerNumber = result.trackerNumber,
                                                        picUrl = result.picUrl
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.fetch_lyrics_failed),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    // 3. 仅应用歌词时传入 offset
                                    onApplyLyricsOnlyClick = { offset ->
                                        scope.launch {
                                            val lyrics = viewModel.fetchLyrics(result, offset) // 关键：传入 offset
                                            if (lyrics != null) {
                                                resultNavigator.navigateBack(
                                                    LyricsSearchResult(
                                                        title = null,
                                                        artist = null,
                                                        album = null,
                                                        lyrics = lyrics,
                                                        date = null,
                                                        trackerNumber = null,
                                                        picUrl = null,
                                                        lyricsOnly = true
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.fetch_lyrics_failed),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
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

    /**
     * 歌词 BottomSheet
     * 只要 lyricsState.song != null 即显示
     */
    val lyricsState = uiState.lyricsState

    if (lyricsState.song != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { viewModel.clearLyrics() },
            containerColor = SaltTheme.colors.background,
            tonalElevation = 0.dp
        ) {
            LyricsBottomSheetContent(
                lyricsState = lyricsState,
                onApply = { lyrics ->
                    val song = lyricsState.song
                    resultNavigator.navigateBack(
                        LyricsSearchResult(
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            lyrics = lyrics,
                            date = song.date,
                            trackerNumber = song.trackerNumber,
                            picUrl = song.picUrl
                        )
                    )
                    viewModel.clearLyrics()
                },
                onApplyLyricsOnly = { lyrics ->
                    resultNavigator.navigateBack(
                        LyricsSearchResult(
                            title = null,
                            artist = null,
                            album = null,
                            lyrics = lyrics,
                            date = null,
                            trackerNumber = null,
                            picUrl = null,
                            lyricsOnly = true
                        )
                    )
                    viewModel.clearLyrics()
                }
            )
        }
    }

}

@Composable
private fun LyricsBottomSheetContent(
    lyricsState: LyricsUiState,
    onApply: (String) -> Unit,
    onApplyLyricsOnly: (String) -> Unit
) {
    val song = lyricsState.song ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(song.title, color = SaltTheme.colors.text, style = SaltTheme.textStyles.main)
        Text(song.artist, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
        Text(song.album, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                lyricsState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = SaltTheme.colors.highlight
                    )
                }

                lyricsState.error != null -> {
                    Text(
                        lyricsState.error,
                        color = SaltTheme.colors.highlight
                    )
                }

                lyricsState.content != null -> {
                    Text(
                        text = lyricsState.content,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = SaltTheme.colors.text
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { lyricsState.content?.let(onApplyLyricsOnly) },
                enabled = lyricsState.content != null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SaltTheme.colors.highlight
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = lyricsState.content != null)
            ) {
                Text(stringResource(R.string.apply_lyrics_only_action))
            }

            Button(
                onClick = { lyricsState.content?.let(onApply) },
                enabled = lyricsState.content != null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaltTheme.colors.highlight
                )
            ) {
                Text(stringResource(R.string.apply_action), color = SaltTheme.colors.onHighlight)
            }
        }
    }
}

@Composable
fun SearchResultItem(
    song: SongSearchResult,
    onPreviewClick: (Long) -> Unit,
    onApplyClick: (Long) -> Unit,
    onApplyLyricsOnlyClick: (Long) -> Unit
) {
    val context = LocalContext.current

    var offset by remember { mutableLongStateOf(0L) } // 偏移量（毫秒）
    var isOffsetVisible by remember { mutableStateOf(false) } // 是否展开调节面板
    // 原图尺寸状态
    var imageSize by remember(song.picUrl) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    LaunchedEffect(song.picUrl) {
        if (song.picUrl.isNotBlank()) {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(song.picUrl)
                .size(Size.ORIGINAL)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val image = result.image
                if (image.width > 0 && image.height > 0) {
                    imageSize = image.width to image.height
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = {
            onPreviewClick(offset)
        })
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LyricoColors.coverPlaceholder)
            ) {
                AsyncImage(
                    model = song.picUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    ),
                    error = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    )
                )

                imageSize?.let { (w, h) ->
                    val isDark = SaltTheme.configs.isDarkTheme
                    val gradientColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
                    val textColor = if (isDark) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                )
                            )
                            .padding(top = 8.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = "${w}×${h}",
                            color = textColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SaltTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = song.artist, fontSize = 13.sp, color = SaltTheme.colors.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)

                // 显示当前偏移量提醒
                if (offset != 0L) {
                    Text(
                        text = "Offset: ${if (offset > 0) "+" else ""}${offset}ms",
                        fontSize = 11.sp,
                        color = SaltTheme.colors.highlight,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    val meta = listOfNotNull(song.album.takeIf { it.isNotBlank() }, song.date.takeIf { it.isNotBlank() }).joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        Text(text = meta, fontSize = 11.sp, color = SaltTheme.colors.subText.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 主按钮：应用 (传递 offset)
                Button(
                    onClick = { onApplyClick(offset) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaltTheme.colors.highlight)
                ) {
                    Text(text = stringResource(R.string.apply_action), color = SaltTheme.colors.onHighlight, fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 调节 Offset 的开关图标
                    IconButton(
                        onClick = { isOffsetVisible = !isOffsetVisible },
                        modifier = Modifier.size(30.dp),
                        colors = IconButtonColors(
                            containerColor = SaltTheme.colors.subBackground,
                            contentColor = SaltTheme.colors.subText,
                            disabledContainerColor = SaltTheme.colors.subBackground,
                            disabledContentColor = SaltTheme.colors.subText
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_timer_24dp),
                            contentDescription = "Adjust Offset",
                            tint = if (isOffsetVisible) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 仅歌词按钮
                    TextButton(
                        onClick = { onApplyLyricsOnlyClick(offset) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(text = stringResource(R.string.apply_lyrics_only_action), fontSize = 11.sp, color = SaltTheme.colors.subText)
                    }

                }
            }
        }
        AnimatedVisibility(
            visible = isOffsetVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OffsetAdjustPanel(
                currentOffset = offset,
                onOffsetChange = { offset = it },
                onReset = { offset = 0L }
            )
        }
        ItemDivider()
    }
}
@Composable
fun OffsetAdjustPanel(
    currentOffset: Long,
    onOffsetChange: (Long) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .background(SaltTheme.colors.subBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 减少单位
        OffsetStepButton("-500") { onOffsetChange(currentOffset - 500) }
        OffsetStepButton("-100") { onOffsetChange(currentOffset - 100) }

        // 数值显示 & 重置
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clickable { onReset() }
        ) {
            Text(
                text = "${if (currentOffset > 0) "+" else ""}${currentOffset}ms",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SaltTheme.colors.highlight
            )
            Text(text = "点击重置", fontSize = 9.sp, color = SaltTheme.colors.subText)
        }

        // 增加单位
        OffsetStepButton("+100") { onOffsetChange(currentOffset + 100) }
        OffsetStepButton("+500") { onOffsetChange(currentOffset + 500) }
    }
}

@Composable
fun OffsetStepButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SaltTheme.colors.background,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, SaltTheme.colors.subText.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = SaltTheme.colors.text
        )
    }
}