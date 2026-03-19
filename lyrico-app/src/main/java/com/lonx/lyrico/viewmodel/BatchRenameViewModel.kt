package com.lonx.lyrico.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.RenamePreview
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.RenameEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import com.lonx.lyrico.R
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

data class BatchRenameUiState(
    val songs: List<SongForBatchRename> = emptyList(),
    val presetFormats: List<String> = emptyList(),
    val previews: List<RenamePreview> = emptyList(),
    val isGeneratingPreview: Boolean = false,
    val isRenamingInProgress: Boolean = false,
    val renameResult: RenameEngine.Result? = null,
    val errorMessage: UiMessage? = null,
    val characterMappingConfig: CharacterMappingConfig? = null
)

data class SongForBatchRename(
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?,
    val fileLastModified: Long = 0L
)

class BatchRenameViewModel(
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchRenameUiState())
    val uiState: StateFlow<BatchRenameUiState> = _uiState

    /** 独立的状态流，用于 combine */
    private val songsFlow = MutableStateFlow<List<SongForBatchRename>>(emptyList())
//    private val renameFormat = MutableStateFlow("@1 - @2")
    val renameFormat = settingsRepository.renameFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDefaults.RENAME_FORMAT)

    init {

        _uiState.value = _uiState.value.copy(
            presetFormats = RenameEngine.getPresetFormats()
        )

        /** 同步 characterMappingConfig 到 uiState */
        viewModelScope.launch {
            settingsRepository.characterMappingConfig.collect { config ->
                _uiState.update {
                    it.copy(characterMappingConfig = config)
                }
            }
        }

        /** preview 自动生成 */
        viewModelScope.launch(Dispatchers.IO) {

            combine(
                songsFlow,
                renameFormat,
                settingsRepository.characterMappingConfig
            ) { songs, format, mapping ->
                Triple(songs, format, mapping)
            }
                .collectLatest { (songs, format, mapping) ->

                    if (songs.isEmpty()) {
                        _uiState.update { it.copy(previews = emptyList()) }
                        return@collectLatest
                    }

                    _uiState.update { it.copy(isGeneratingPreview = true) }

                    try {

                        val songsForRename = songs.mapNotNull { song ->

                            val tagData = song.tagData ?: loadTagData(song.filePath)

                            tagData?.let {
                                RenameEngine.SongForRename(
                                    originalPath = song.filePath,
                                    tagData = it
                                )
                            }
                        }

                        if (songsForRename.isEmpty()) {
                            _uiState.update {
                                it.copy(
                                    previews = emptyList(),
                                    isGeneratingPreview = false,
                                    errorMessage = UiMessage.StringResource(R.string.no_tag_data)
                                )
                            }
                            return@collectLatest
                        }

                        val request = RenameEngine.RenameRequest(
                            songs = songsForRename,
                            format = format,
                            characterMappingRules = mapping.rules
                        )

                        val previews = RenameEngine.generatePreviews(request)

                        _uiState.update {
                            it.copy(
                                previews = previews,
                                isGeneratingPreview = false
                            )
                        }

                    } catch (e: Exception) {

                        _uiState.update {
                            it.copy(
                                isGeneratingPreview = false,
                                errorMessage = UiMessage.DynamicString(e.message)
                            )
                        }
                    }
                }
        }
    }

    /**
     * 设置歌曲列表
     */
    fun setSongs(songs: List<SongForBatchRename>) {

        songsFlow.value = songs

        _uiState.update {
            it.copy(
                songs = songs,
                renameResult = null,
                errorMessage = null
            )
        }
    }

    /**
     * 修改格式
     */
    fun saveFormat(format: String) {
        viewModelScope.launch {
            settingsRepository.saveRenameFormat(format)
        }
    }

    /**
     * 执行重命名
     */
    fun executeRename() {

        val currentState = _uiState.value
        val previews = currentState.previews

        if (previews.isEmpty()) return

        val timeMap = currentState.songs.associate {
            it.filePath to it.fileLastModified
        }

        viewModelScope.launch(Dispatchers.IO) {

            try {

                _uiState.update {
                    it.copy(
                        isRenamingInProgress = true,
                        errorMessage = null
                    )
                }

                val sortedPreviews = previews.sortedBy { preview ->
                    timeMap[preview.originalPath] ?: 0L
                }

                val result = RenameEngine.renameFiles(sortedPreviews)

                _uiState.update {
                    it.copy(
                        renameResult = result,
                        isRenamingInProgress = false
                    )
                }

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(
                        isRenamingInProgress = false,
                        errorMessage = UiMessage.DynamicString(e.message)
                    )
                }
            }
        }
    }

    /**
     * 读取标签
     */
    private suspend fun loadTagData(filePath: String): AudioTagData? {

        return try {

            val uri = Uri.fromFile(File(filePath))

            appContext.contentResolver
                .openFileDescriptor(uri, "r")
                ?.use { descriptor ->

                    AudioTagReader.read(descriptor, true)
                }

        } catch (e: Exception) {
            null
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(renameResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateCharacterMappingInRule(
        ruleId: String,
        character: String,
        replacementChar: String?
    ) {

        viewModelScope.launch(Dispatchers.IO) {

            val currentConfig = _uiState.value.characterMappingConfig ?: return@launch

            val rule = currentConfig.rules.find { it.id == ruleId } ?: return@launch
            
            // 更新该字符的映射
            val updatedMappings = rule.charMappings.toMutableMap()

            updatedMappings[character] = replacementChar ?: ""

            settingsRepository.updateCharacterMappingInRule(
                ruleId,
                updatedMappings
            )
        }
    }
}
