package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import dev.jeziellago.compose.markdowntext.MarkdownText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun UpdateDialog(
    show: Boolean,
    versionName: String,
    releaseNote: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()

    WindowDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(
            id = R.string.dialog_title_update_available,
            versionName
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {

            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                MarkdownText(
                    modifier = Modifier
                        .padding(12.dp)
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                    markdown = releaseNote
                )
            }

            Row {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                TextButton(
                    text = stringResource(R.string.dialog_action_go_update),
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}