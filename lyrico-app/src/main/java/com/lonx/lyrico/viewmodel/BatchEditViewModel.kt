package com.lonx.lyrico.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 可批量编辑的标签字段枚举
 */
enum class BatchEditField(val labelResId: Int) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM_ARTIST(R.string.label_album_artist),
    ALBUM(R.string.label_album),
    DATE(R.string.label_date),
    GENRE(R.string.label_genre),
    TRACK_NUMBER(R.string.label_track_number),
    DISC_NUMBER(R.string.label_disc_number),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    COPYRIGHT(R.string.label_copyright),
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover),
    RATING(R.string.label_rating),
}

data class BatchEditUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val saveTotal: Int = 0,
    val saveSuccess: Boolean? = null,
    val saveResultMessage: UiMessage? = null,
    val errorMessage: UiMessage? = null,

    /** 各字段当前编辑值（"<keep>"表示不修改） */
    val title: String = "<keep>",
    val artist: String = "<keep>",
    val albumArtist: String = "<keep>",
    val album: String = "<keep>",
    val date: String = "<keep>",
    val genre: String = "<keep>",
    val trackNumber: String = "<keep>",
    val discNumber: String = "<keep>",
    val composer: String = "<keep>",
    val lyricist: String = "<keep>",
    val copyright: String = "<keep>",
    val comment: String = "<keep>",
    val lyrics: String = "<keep>",
    val rating: Int = 0,
    val ratingModified: Boolean = false,

    /** 封面相关 */
    val coverUri: Any? = null,
    val removeCover: Boolean = false,

    /** 歌词偏移（毫秒） */
    val lyricsOffset: String = "",

    /** 自定义标签 */
    val customFields: List<CustomTagField> = emptyList()
)

class BatchEditViewModel(
    private val songRepository: SongRepository,
    private val selectionManager: SharedSelectionManager
) : ViewModel() {

    private val TAG = "BatchEditVM"

    private val _uiState = MutableStateFlow(BatchEditUiState())
    val uiState: StateFlow<BatchEditUiState> = _uiState.asStateFlow()

    /** 保存选中的文件uri */
    private var selectedUris: List<String> = emptyList()

    init {
        val paths = selectionManager.selectedUris.value.toList()
        selectedUris = paths
        _uiState.update { it.copy(songCount = paths.size) }
    }



    // ── 标签值更新 ──────────────────────────────────────────

    fun updateTitle(value: String) { _uiState.update { it.copy(title = value) } }
    fun updateArtist(value: String) { _uiState.update { it.copy(artist = value) } }
    fun updateAlbumArtist(value: String) { _uiState.update { it.copy(albumArtist = value) } }
    fun updateAlbum(value: String) { _uiState.update { it.copy(album = value) } }
    fun updateDate(value: String) { _uiState.update { it.copy(date = value) } }
    fun updateGenre(value: String) { _uiState.update { it.copy(genre = value) } }
    fun updateTrackNumber(value: String) { _uiState.update { it.copy(trackNumber = value) } }
    fun updateDiscNumber(value: String) { _uiState.update { it.copy(discNumber = value) } }
    fun updateComposer(value: String) { _uiState.update { it.copy(composer = value) } }
    fun updateLyricist(value: String) { _uiState.update { it.copy(lyricist = value) } }
    fun updateCopyright(value: String) { _uiState.update { it.copy(copyright = value) } }
    fun updateComment(value: String) { _uiState.update { it.copy(comment = value) } }
    fun updateLyrics(value: String) { _uiState.update { it.copy(lyrics = value) } }
    fun updateRating(value: Int) { 
        _uiState.update { it.copy(rating = value, ratingModified = true) } 
    }
    fun resetRating() { 
        _uiState.update { it.copy(rating = 0, ratingModified = false) } 
    }

    // ── 歌词偏移 ──────────────────────────────────────────

    fun updateLyricsOffset(value: String) { _uiState.update { it.copy(lyricsOffset = value) } }

    // ── 自定义标签 ──────────────────────────────────────────

    fun addCustomField(field: CustomTagField) {
        _uiState.update { it.copy(customFields = it.customFields + field) }
    }

    fun updateCustomField(index: Int, key: String, value: String) {
        _uiState.update {
            it.copy(
                customFields = it.customFields.toMutableList().apply {
                    this[index] = this[index].copy(key = key, value = value)
                }
            )
        }
    }

    fun removeCustomField(index: Int) {
        _uiState.update {
            it.copy(
                customFields = it.customFields.toMutableList().apply {
                    removeAt(index)
                }
            )
        }
    }


    // ── 封面管理 ──────────────────────────────────────────

    fun updateCover(uri: Uri) {
        _uiState.update { it.copy(coverUri = uri, removeCover = false) }
    }

    fun removeCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = true) }
    }

    fun revertCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = false) }
    }

    // ── 批量保存 ──────────────────────────────────────────

    fun saveBatchEdit() {
        val state = _uiState.value
        if (state.isSaving || selectedUris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveProgress = 0,
                    saveTotal = selectedUris.size,
                    saveSuccess = null,
                    saveResultMessage = null,
                    errorMessage = null
                )
            }

            var successCount = 0
            var failCount = 0

            for ((index, path) in selectedUris.withIndex()) {
                try {
                    val success = withContext(Dispatchers.IO) {
                        updateAudioTags(path, state)
                    }
                    if (success) successCount++ else failCount++
                } catch (e: Exception) {
                    Log.e(TAG, "批量编辑失败: $path", e)
                    failCount++
                }

                _uiState.update { it.copy(saveProgress = index + 1) }
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = failCount == 0,
                    saveResultMessage = UiMessage.StringResource(
                        R.string.batch_edit_result_summary,
                        successCount,
                        selectedUris.size,
                        failCount
                    )
                )
            }
        }
    }

    /**
     * 处理单首歌曲的批量编辑
     * 先读取原始标签，再合并用户选择的字段，最后写回
     */
    private suspend fun updateAudioTags(uri: String, state: BatchEditUiState): Boolean {
        // 读取当前标签
        val uriString = uri
        val currentTag = try {
            songRepository.readAudioTagData(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "无法读取标签: $uri", e)
            return false
        }

        // 按用户启用的字段合并数据
        val mergedTag = buildMergedTag(currentTag, state)

        // 写入文件
        return try {
            val success = songRepository.overwriteAudioTags(uriString, mergedTag)
            if (success) {
                songRepository.updateSongMetadata(mergedTag, uriString, System.currentTimeMillis())
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "写入标签失败: $uri", e)
            false
        }
    }

    /**
     * 根据用户编辑的值，将批量编辑值合并到原标签中
     * 值为"<keep>"时表示不修改该字段
     */
    private fun buildMergedTag(original: AudioTagData, state: BatchEditUiState): AudioTagData {
        var tag = original

        if (state.title != "<keep>") tag = tag.copy(title = state.title)
        if (state.artist != "<keep>") tag = tag.copy(artist = state.artist)
        if (state.albumArtist != "<keep>") tag = tag.copy(albumArtist = state.albumArtist)
        if (state.album != "<keep>") tag = tag.copy(album = state.album)
        if (state.date != "<keep>") tag = tag.copy(date = state.date)
        if (state.genre != "<keep>") tag = tag.copy(genre = state.genre)
        if (state.trackNumber != "<keep>") tag = tag.copy(trackNumber = state.trackNumber)
        if (state.discNumber != "<keep>") tag = tag.copy(discNumber = state.discNumber.toIntOrNull())
        if (state.composer != "<keep>") tag = tag.copy(composer = state.composer)
        if (state.lyricist != "<keep>") tag = tag.copy(lyricist = state.lyricist)
        if (state.copyright != "<keep>") tag = tag.copy(copyright = state.copyright)
        if (state.comment != "<keep>") tag = tag.copy(comment = state.comment)
        if (state.lyrics != "<keep>") tag = tag.copy(lyrics = state.lyrics)
        
        // 处理 rating - 只在明确修改时才更新
        if (state.ratingModified) tag = tag.copy(rating = state.rating)

        // 处理覆盖图
        if (state.removeCover) {
            tag = tag.copy(picUrl = "")
        } else if (state.coverUri != null) {
            tag = tag.copy(picUrl = state.coverUri.toString())
        }

        // 处理歌词偏移（直接修改歌词文本中的时间戳）
        if (state.lyricsOffset.isNotBlank() && tag.lyrics != null) {
            val offsetValue = parseLyricsOffset(state.lyricsOffset)
            if (offsetValue != 0) {
                val shiftedLyrics = LyricsUtils.shiftLyricsOffset(tag.lyrics!!, offsetValue.toLong())
                tag = tag.copy(lyrics = shiftedLyrics)
            }
        }

        // 处理自定义标签
        if (state.customFields.isNotEmpty()) {
            tag = tag.copy(customFields = tag.customFields.toMutableList().apply {
                state.customFields.forEach { newField ->
                    val existingIndex = indexOfFirst { it.key == newField.key }
                    if (existingIndex >= 0) {
                        this[existingIndex] = newField
                    } else {
                        add(newField)
                    }
                }
            })
        }

        return tag
    }

    /**
     * 解析歌词偏移值
     * 支持正负号，未填写正负号默认为正
     */
    private fun parseLyricsOffset(input: String): Int {
        return try {
            val trimmed = input.trim()
            if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
                trimmed.toInt()
            } else {
                // 未填写正负号，默认为正
                trimmed.toInt()
            }
        } catch (e: NumberFormatException) {
            0
        }
    }

    // ── 状态清理 ──────────────────────────────────────────

    fun clearSaveResult() {
        _uiState.update { it.copy(saveSuccess = null, saveResultMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 获取同专辑歌曲封面
     * 优先使用同专辑且同歌手的查询结果作为封面
     * 如果专辑或艺术家字段被修改过，则使用修改后的值进行查询
     */
    suspend fun getSameAlbumCovers(): List<Pair<String, Any?>> {
        val uiState = _uiState.value
        val editedAlbum = uiState.album
        val editedArtist = uiState.artist

        // 确定使用的专辑和艺术家值
        val targetAlbum: String
        val targetArtist: String

        if (editedAlbum != "<keep>" && editedAlbum.isNotBlank()) {
            // 如果专辑被修改过，使用修改后的值
            targetAlbum = editedAlbum
            targetArtist = if (editedArtist != "<keep>" && editedArtist.isNotBlank()) editedArtist else ""
        } else {
            // 如果专辑没被修改，检查所有选中歌曲的专辑和艺术家是否一致
            var commonAlbum: String? = null
            var commonArtist: String? = null
            var hasMismatch = false

            for (uri in selectedUris) {
                try {
                    val tagData = songRepository.readAudioTagData(uri)
                    val album = tagData.album
                    val artist = tagData.artist

                    if (commonAlbum == null && commonArtist == null) {
                        commonAlbum = album
                        commonArtist = artist
                    } else {
                        if (album != commonAlbum || artist != commonArtist) {
                            hasMismatch = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取歌曲标签失败: $uri", e)
                    hasMismatch = true
                    break
                }
            }

            // 如果存在不一致的情况，更新错误消息并返回空列表
            if (hasMismatch || commonAlbum.isNullOrBlank()) {
                _uiState.update { 
                    it.copy(errorMessage = UiMessage.StringResource(R.string.batch_edit_cover_mismatch)) 
                }
                return emptyList()
            }

            targetAlbum = commonAlbum
            targetArtist = if (editedArtist != "<keep>" && editedArtist.isNotBlank()) editedArtist else (commonArtist ?: "")
        }

        // 清除之前的错误消息
        _uiState.update { it.copy(errorMessage = null) }

        // 查询同专辑的歌曲封面
        val sameAlbumSongs = songRepository.getSongsByAlbum(targetAlbum, targetArtist)
        val covers = mutableListOf<Pair<String, Any?>>()

        for (song in sameAlbumSongs) {
            try {
                val tagData = songRepository.readAudioTagData(song.uri)
                val cover = tagData.pictures.firstOrNull()?.data ?: tagData.picUrl
                if (cover != null) {
                    val title = "${song.title} - ${song.artist}"
                    covers.add(title to cover)
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取同专辑歌曲封面失败: ${song.uri}", e)
            }
        }

        return covers
    }

    override fun onCleared() {
        super.onCleared()
        selectionManager.clearAll()
    }
}
