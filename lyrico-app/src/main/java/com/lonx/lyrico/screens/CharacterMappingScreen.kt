package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.CharacterMappingRule
import com.lonx.lyrico.data.model.ReplacementCharOption
import com.lonx.lyrico.data.model.toReplacementOption
import com.lonx.lyrico.viewmodel.BatchRenameViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "character_mapping")
fun CharacterMappingScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchRenameViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val characterMappingConfig = uiState.characterMappingConfig
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.configure_character_mapping),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.character_mapping_description),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(12.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                overscrollEffect = null,
            ) {
                if (characterMappingConfig != null && characterMappingConfig.rules.isNotEmpty()) {
                    items(items=characterMappingConfig.rules){ rule ->
                        CharacterMappingRuleSection(
                            rule = rule,
                            onCharacterMappingChanged = { character, replacementChar ->
                                viewModel.updateCharacterMappingInRule(
                                    rule.id,
                                    character,
                                    replacementChar
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterMappingRuleSection(
    rule: CharacterMappingRule,
    onCharacterMappingChanged: (character: String, replacementChar: String?) -> Unit
) {
    if (rule.charMappings.isEmpty()) {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.no_character_mappings),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val optionLabels = listOf(
        stringResource(R.string.replacement_not_selected)
    ) + ReplacementCharOption.entries.map { option ->
        stringResource(option.labelRes)
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp)
    ) {
        rule.charMappings.entries.forEach { (character, currentReplacement) ->
            val currentOption = currentReplacement.toReplacementOption()
            val selectedIndex = currentOption?.let { ReplacementCharOption.entries.indexOf(it) + 1 } ?: 0

            SuperDropdown(
                title = stringResource(R.string.character_to_replace, character),
                summary = stringResource(R.string.character_replacement_selector_subtitle),
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    val replacementValue = if (index == 0) {
                        null
                    } else {
                        ReplacementCharOption.entries[index - 1].value
                    }
                    onCharacterMappingChanged(character, replacementValue)
                }
            )
        }
    }
}
