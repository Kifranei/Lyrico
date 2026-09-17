package com.lonx.lyrico.ui.components.update

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.dto.ReleaseInfo
import com.lonx.lyrico.utils.ReleaseAssetMatcher
import com.lonx.lyrico.utils.UpdateDownloadManager
import com.lonx.lyrico.utils.UpdateDownloadState
import com.lonx.lyrico.viewmodel.UpdateUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 无更新 / 正在检查 / 检查失败时居中展示的那块内容。 */
@Composable
fun UpdateCenteredStatus(
    state: UpdateUiState,
    showCurrentLog: Boolean,
    onToggleCurrentLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            UpdateUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val transition = rememberInfiniteTransition(label = "UpdateChecking")
                    val rotation by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "UpdateCheckingRotation"
                    )

                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = rotation },
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_checking),
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            is UpdateUiState.Ready -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_already_latest),
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                if (state.release.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ChangelogToggleLink(
                        text = stringResource(
                            if (showCurrentLog) {
                                R.string.update_collapse_changelog
                            } else {
                                R.string.update_current_version_log
                            }
                        ),
                        onClick = onToggleCurrentLog
                    )
                }
            }

            is UpdateUiState.Error -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_check_failed),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                state.message.asString()?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detail,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChangelogToggleLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val linkColor = MiuixTheme.colorScheme.primary
        Text(
            text = text,
            fontSize = 13.sp,
            color = linkColor,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = linkColor
        )
    }
}

/** 发现新版本时顶部那张卡：版本号、架构标签、发布日期。 */
@Composable
fun UpdateHeaderCard(
    release: ReleaseInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = release.versionName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UpdateTag(
                    text = stringResource(R.string.update_new_version_found),
                    background = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MiuixTheme.colorScheme.primary
                )

                // 分包下载：告诉用户这次装的是哪个架构的包
                release.matchedAsset
                    ?.let { ReleaseAssetMatcher.detectArchLabel(it.name) }
                    ?.let { archLabel ->
                        UpdateTag(
                            text = archLabel,
                            background = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            release.matchedAsset?.let { asset ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.update_asset_size,
                        asset.name,
                        UpdateDownloadManager.formatBytes(asset.sizeBytes)
                    ),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UpdateTag(
    text: String,
    background: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 更新日志卡片。 */
@Composable
fun UpdateChangelogCard(
    title: String,
    changelog: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ReleaseMarkdown(
                markdown = changelog.ifBlank { stringResource(R.string.update_empty_changelog) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 底部固定的主操作按钮。
 * 下载中时按钮本身就是进度条：蓝色填充随进度推进，中间显示百分比和速度。
 */
@Composable
fun UpdateBottomActionButton(
    hasUpdate: Boolean,
    isChecking: Boolean,
    downloadState: UpdateDownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonHeight = 50.dp
    val pillShape = CircleShape
    val accent = MiuixTheme.colorScheme.primary

    when (downloadState) {
        is UpdateDownloadState.Downloading -> {
            val animatedProgress by animateFloatAsState(
                targetValue = downloadState.progress.coerceIn(0.01f, 1f),
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "UpdateDownloadProgress"
            )
            val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)
            val speedText = downloadState.speedBytesPerSec
                .takeIf { it > 0 }
                ?.let { UpdateDownloadManager.formatSpeed(it) }
            val label = if (speedText != null) "$percent% · $speedText" else "$percent%"

            // 文字要压在填充条上，填充过半时翻成白色才看得清
            val textColor by animateColorAsState(
                targetValue = if (animatedProgress >= 0.45f) Color.White else accent,
                animationSpec = tween(200),
                label = "UpdateDownloadTextColor"
            )

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(accent)
                )
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }

        is UpdateDownloadState.Completed -> UpdateActionPill(
            text = stringResource(R.string.update_install),
            background = accent,
            contentColor = Color.White,
            onClick = onClick,
            modifier = modifier
        )

        is UpdateDownloadState.Failed -> UpdateActionPill(
            text = stringResource(R.string.update_download_retry),
            background = accent,
            contentColor = Color.White,
            onClick = onClick,
            modifier = modifier
        )

        UpdateDownloadState.Idle -> {
            val background = if (hasUpdate) accent else MiuixTheme.colorScheme.surfaceContainerHigh
            val contentColor = when {
                hasUpdate -> Color.White
                isChecking -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                else -> MiuixTheme.colorScheme.onSurface
            }
            UpdateActionPill(
                text = when {
                    hasUpdate -> stringResource(R.string.update_download)
                    isChecking -> stringResource(R.string.update_checking)
                    else -> stringResource(R.string.update_check_button)
                },
                background = background,
                contentColor = contentColor,
                enabled = !isChecking,
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun UpdateActionPill(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
