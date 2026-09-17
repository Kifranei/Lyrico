package com.lonx.lyrico.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.update.ChangelogToggleLink
import com.lonx.lyrico.ui.components.update.UpdateBottomActionButton
import com.lonx.lyrico.ui.components.update.UpdateCenteredStatus
import com.lonx.lyrico.ui.components.update.UpdateChangelogCard
import com.lonx.lyrico.ui.components.update.UpdateHeaderCard
import com.lonx.lyrico.utils.UpdateDownloadManager
import com.lonx.lyrico.utils.UpdateDownloadState
import com.lonx.lyrico.viewmodel.UpdateUiState
import com.lonx.lyrico.viewmodel.UpdateViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 更新页的三种版式，取决于有没有新版本、以及用户有没有展开当前版本日志。 */
private enum class UpdateScreenMode {
    /** 无更新：一块居中的状态文案。 */
    Centered,

    /** 无更新但展开了当前版本的更新日志。 */
    CurrentLog,

    /** 有新版本：版本卡片 + 更新日志。 */
    NewUpdate
}

@Composable
@Destination<RootGraph>(route = "update")
fun UpdateScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: UpdateViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showCurrentLog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()

    val readyState = state as? UpdateUiState.Ready
    val hasUpdate = readyState?.hasUpdate == true
    val isChecking = state is UpdateUiState.Loading

    // 包可能上次就下好了，进页面先认领已有文件，免得重复下载
    LaunchedEffect(readyState?.release?.matchedAsset) {
        val asset = readyState?.release?.matchedAsset ?: return@LaunchedEffect
        if (!readyState.hasUpdate) return@LaunchedEffect
        UpdateDownloadManager.checkExistingApk(
            context = context,
            asset = asset,
            expectedVersion = readyState.release.versionName
        )
    }

    val displayMode = when {
        hasUpdate -> UpdateScreenMode.NewUpdate
        showCurrentLog && readyState != null -> UpdateScreenMode.CurrentLog
        else -> UpdateScreenMode.Centered
    }

    val surface = MiuixTheme.colorScheme.surface

    Scaffold(
        containerColor = surface,
        topBar = {
            SmallTopAppBar(
                title = if (displayMode == UpdateScreenMode.Centered) {
                    ""
                } else {
                    stringResource(R.string.update_title)
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = stringResource(R.string.update_settings)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val listContentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = bottomInset + 116.dp
            )

            Crossfade(
                targetState = displayMode,
                animationSpec = tween(280),
                label = "UpdateScreenCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                when (mode) {
                    UpdateScreenMode.Centered -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = paddingValues.calculateTopPadding(),
                                bottom = bottomInset + 80.dp
                            )
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        UpdateCenteredStatus(
                            state = state,
                            showCurrentLog = showCurrentLog,
                            onToggleCurrentLog = { showCurrentLog = !showCurrentLog }
                        )
                    }

                    UpdateScreenMode.CurrentLog -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listContentPadding,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "current_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} · " +
                                        stringResource(R.string.update_already_latest),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ChangelogToggleLink(
                                    text = stringResource(R.string.update_collapse_changelog),
                                    onClick = { showCurrentLog = false }
                                )
                            }
                        }

                        readyState?.release?.let { release ->
                            item(key = "current_changelog") {
                                UpdateChangelogCard(
                                    title = stringResource(R.string.update_current_version_log),
                                    changelog = release.releaseNotes
                                )
                            }
                        }
                    }

                    UpdateScreenMode.NewUpdate -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listContentPadding,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        readyState?.release?.let { release ->
                            item(key = "update_header") {
                                UpdateHeaderCard(release = release)
                            }
                            item(key = "update_changelog") {
                                UpdateChangelogCard(
                                    title = stringResource(R.string.update_view_changelog),
                                    changelog = release.releaseNotes
                                )
                            }
                        }
                    }
                }
            }

            // 底部固定操作区，用渐变让列表滚到按钮下方时不至于糊在一起
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                surface.copy(alpha = 0.85f),
                                surface
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                UpdateBottomActionButton(
                    hasUpdate = hasUpdate,
                    isChecking = isChecking,
                    downloadState = downloadState,
                    onClick = {
                        when (val current = downloadState) {
                            is UpdateDownloadState.Downloading -> Unit

                            is UpdateDownloadState.Completed ->
                                UpdateDownloadManager.installApk(context, current.apkFile)

                            is UpdateDownloadState.Failed -> {
                                val release = readyState?.release
                                val asset = release?.matchedAsset
                                if (asset != null) {
                                    UpdateDownloadManager.startDownload(
                                        context = context,
                                        asset = asset,
                                        expectedVersion = release.versionName
                                    )
                                } else {
                                    viewModel.checkUpdate()
                                }
                            }

                            UpdateDownloadState.Idle -> {
                                val release = readyState?.release
                                when {
                                    release == null || !hasUpdate -> viewModel.checkUpdate()

                                    // 没有能匹配本机 ABI 的包时退回发布页，让用户自己挑
                                    release.matchedAsset == null -> uriHandler.openUri(release.url)

                                    else -> UpdateDownloadManager.startDownload(
                                        context = context,
                                        asset = release.matchedAsset,
                                        expectedVersion = release.versionName
                                    )
                                }
                            }
                        }
                    }
                )
            }

            UpdateSettingsSheet(
                show = showSettings,
                checkUpdateEnabled = checkUpdateEnabled,
                onCheckUpdateEnabledChange = viewModel::setCheckUpdateEnabled,
                onDismissRequest = { showSettings = false }
            )
        }
    }
}

/** 更新页右上角齿轮打开的设置面板。 */
@Composable
private fun UpdateSettingsSheet(
    show: Boolean,
    checkUpdateEnabled: Boolean,
    onCheckUpdateEnabledChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    WindowBottomSheet(
        title = stringResource(R.string.update_settings),
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.about_auto_check_update),
                    summary = stringResource(R.string.about_auto_check_update_sub),
                    checked = checkUpdateEnabled,
                    onCheckedChange = onCheckUpdateEnabledChange
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
