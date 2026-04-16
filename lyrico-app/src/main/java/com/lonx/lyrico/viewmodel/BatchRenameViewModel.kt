package com.lonx.lyrico.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.RenamePreview
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.RenameEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class BatchRenameUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val saveTotal: Int = 0,
    val saveSuccess: Boolean? = null,
    val saveResultMessage: UiMessage? = null,
    val errorMessage: UiMessage? = null,
    val songs: List<SongForBatchRename> = emptyList(),
    val presetFormats: List<String> = emptyList(),
    val previews: List<RenamePreview> = emptyList(),
    val isGeneratingPreview: Boolean = false,
    val isRenamingInProgress: Boolean = false,
    val renameResult: RenameEngine.Result? = null,
    val characterMappingConfig: CharacterMappingConfig? = null
)

data class SongForBatchRename(
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?
)

class BatchRenameViewModel(
    private val settingsRepository: SettingsRepository,
    private val songRepository: SongRepository,
    private val selectionManager: SharedSelectionManager,
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

        val selectedUris = selectionManager.selectedUris.value
        if (selectedUris.isNotEmpty()) {
            // 从数据库获取歌曲信息，避免重复读取文件
            viewModelScope.launch(Dispatchers.IO) {
                val songList = selectedUris.mapNotNull { path ->
                    val songEntity = songRepository.getSongByUri(path)
                    songEntity?.let {
                        SongForBatchRename(
                            filePath = it.filePath,
                            fileName = it.fileName,
                            tagData = convertToAudioTagData(it)
                        )
                    }
                }
                setSongs(songList)
            }
        }

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
                            song.tagData?.let { tagData ->
                                RenameEngine.SongForRename(
                                    originalPath = song.filePath,
                                    tagData = tagData
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
    private fun setSongs(songs: List<SongForBatchRename>) {

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


        viewModelScope.launch(Dispatchers.IO) {

            try {

                _uiState.update {
                    it.copy(
                        isRenamingInProgress = true,
                        saveProgress = 0,
                        saveTotal = previews.size,
                        errorMessage = null
                    )
                }


                val result = RenameEngine.renameFiles(previews) { progress, total ->
                    _uiState.update {
                        it.copy(
                            saveProgress = progress,
                            saveTotal = total
                        )
                    }
                }

                // 更新成功重命名的预览，将原路径更新为新路径，让用户看到重命名后的结果
                val updatedPreviews = previews.map { preview ->
                    if (result.successful.contains(preview)) {
                        preview.copy(originalPath = preview.newPath)
                    } else {
                        preview
                    }
                }

                // 更新 songsFlow 中的文件路径，确保后续操作使用正确的路径
                val currentSongs = songsFlow.value.toMutableList()
                result.successful.forEach { successfulPreview ->
                    val index = currentSongs.indexOfFirst { it.filePath == successfulPreview.originalPath }
                    if (index != -1) {
                        currentSongs[index] = currentSongs[index].copy(
                            filePath = successfulPreview.newPath,
                            fileName = successfulPreview.newPath.substringAfterLast('/')
                        )
                    }
                }
                songsFlow.value = currentSongs

                _uiState.update {
                    it.copy(
                        previews = updatedPreviews,
                        renameResult = result,
                        isRenamingInProgress = false,
                        saveProgress = 0,
                        saveTotal = 0
                    )
                }

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(
                        isRenamingInProgress = false,
                        saveProgress = 0,
                        saveTotal = 0,
                        errorMessage = UiMessage.DynamicString(e.message)
                    )
                }
            }
        }
    }

    /**
     * 将 SongEntity 转换为 AudioTagData
     */
    private fun convertToAudioTagData(song: SongEntity): AudioTagData {
        return AudioTagData(
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.albumArtist,
            genre = song.genre,
            date = song.date,
            trackNumber = song.trackerNumber,
            discNumber = song.discNumber,
            composer = song.composer,
            lyricist = song.lyricist,
            comment = song.comment,
            lyrics = song.lyrics,
            copyright = song.copyright,
            rating = song.rating,
            fileName = song.fileName,
            durationMilliseconds = song.durationMilliseconds,
            bitrate = song.bitrate,
            sampleRate = song.sampleRate,
            channels = song.channels
        )
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
    override fun onCleared() {
        super.onCleared()
        selectionManager.clearAll()
    }
}
