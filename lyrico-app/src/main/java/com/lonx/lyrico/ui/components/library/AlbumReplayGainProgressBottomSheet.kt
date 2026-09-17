package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.viewmodel.AlbumActionsUiState
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlbumReplayGainProgressBottomSheet(
    uiState: AlbumActionsUiState,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onAbort: () -> Unit
) {
    ActionBottomSheet(
        show = uiState.showAlbumReplayGainProgressDialog,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = !uiState.isCalculatingAlbumReplayGain,
        title = uiState.albumName,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val progress = uiState.albumReplayGainProgress ?: 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            uiState.isCalculatingAlbumReplayGain -> stringResource(R.string.album_replay_gain_calculating)
                            else -> stringResource(
                                R.string.batch_replay_gain_total_time,
                                uiState.albumReplayGainTotalTimeMillis / 1000.0
                            )
                        },
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )

                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.batch_replay_gain_success,
                                    uiState.albumReplayGainWrittenCount
                                ),
                                style = MiuixTheme.textStyles.main
                            )
                            Text(
                                text = "${uiState.albumReplayGainWrittenCount} / ${uiState.albumReplayGainSongCount}",
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }
                    }



                Spacer(modifier = Modifier.height(12.dp))
            }
        },
        endAction = {
            TextButton(
                colors = ButtonColors(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.primary,
                    disabledContainerColor = MiuixTheme.colorScheme.surface,
                    disabledContentColor = MiuixTheme.colorScheme.disabledPrimary
                ),
                onClick = {
                    if (uiState.isCalculatingAlbumReplayGain) {
                        onAbort()
                    } else {
                        onDismissRequest()
                    }
                }
            ) {
                Text(
                    text = when {
                        uiState.isCalculatingAlbumReplayGain -> stringResource(R.string.action_abort)
                        else -> stringResource(R.string.action_close)
                    },
                    color = when {
                        uiState.isCalculatingAlbumReplayGain -> MiuixTheme.colorScheme.error
                        else -> MiuixTheme.colorScheme.primary
                    }
                )
            }
        }
    )
}