package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.search.LocalSearchUiState
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.search.SongSearchRepository
import com.lonx.lyrico.utils.AdvancedSearch
import com.lonx.lyrico.utils.AdvancedSearchCondition
import com.lonx.lyrico.utils.AdvancedSearchJoinMode
import com.lonx.lyrico.utils.AdvancedSearchOperator
import com.lonx.lyrico.utils.TagTextField
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalSearchViewModel(
    private val songSearchRepository: SongSearchRepository,
    private val songLibraryRepository: SongLibraryRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val advancedSearchEnabled = MutableStateFlow(false)
    private val advancedSearchJoinMode = MutableStateFlow(AdvancedSearchJoinMode.AND)
    private val advancedSearchConditions = MutableStateFlow(listOf(AdvancedSearchCondition()))

    val searchQuery: StateFlow<String> = query
    val isAdvancedSearchEnabled: StateFlow<Boolean> = advancedSearchEnabled
    val advancedJoinMode: StateFlow<AdvancedSearchJoinMode> = advancedSearchJoinMode
    val advancedConditions: StateFlow<List<AdvancedSearchCondition>> = advancedSearchConditions

    val uiState: StateFlow<LocalSearchUiState> = combine(
        query,
        advancedSearchEnabled,
        advancedSearchJoinMode,
        advancedSearchConditions,
        settingsRepository.lyricIndexEnabled.distinctUntilChanged()
    ) { keyword, advancedEnabled, joinMode, conditions, lyricIndexEnabled ->
        SearchInput(keyword, advancedEnabled, joinMode, conditions, lyricIndexEnabled)
    }
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { input ->
            if (input.advancedEnabled) {
                songLibraryRepository.observeSongs(SortBy.TITLE, SortOrder.ASC)
                    .map { songs ->
                        LocalSearchUiState(
                            query = input.keyword,
                            lyricSearchEnabled = input.lyricSearchEnabled,
                            songs = songs.filter { song ->
                                AdvancedSearch.matches(song, input.conditions, input.joinMode)
                            }
                        )
                    }
            } else if (input.keyword.isBlank()) {
                flowOf(
                    LocalSearchUiState(
                        query = input.keyword,
                        lyricSearchEnabled = input.lyricSearchEnabled
                    )
                )
            } else {
                combine(
                    songSearchRepository.searchSongsForLocalSearch(input.keyword),
                    libraryIndexRepository.searchAlbums(input.keyword),
                    libraryIndexRepository.searchArtists(input.keyword),
                    if (input.lyricSearchEnabled) {
                        songSearchRepository.searchLyricsForLocalSearch(input.keyword)
                    } else {
                        flowOf(emptyList())
                    }
                ) { songs, albums, artists, lyricMatches ->
                    LocalSearchUiState(
                        query = input.keyword,
                        lyricSearchEnabled = input.lyricSearchEnabled,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        lyricMatches = lyricMatches
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LocalSearchUiState()
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setAdvancedSearchEnabled(value: Boolean) {
        advancedSearchEnabled.value = value
    }

    fun setAdvancedJoinMode(value: AdvancedSearchJoinMode) {
        advancedSearchJoinMode.value = value
    }

    fun addAdvancedCondition() {
        advancedSearchConditions.value = advancedSearchConditions.value + AdvancedSearchCondition()
    }

    fun removeAdvancedCondition(index: Int) {
        val current = advancedSearchConditions.value
        advancedSearchConditions.value = current
            .filterIndexed { conditionIndex, _ -> conditionIndex != index }
            .ifEmpty { listOf(AdvancedSearchCondition()) }
    }

    fun updateAdvancedConditionField(index: Int, field: TagTextField) {
        updateAdvancedCondition(index) { it.copy(field = field) }
    }

    fun updateAdvancedConditionOperator(index: Int, operator: AdvancedSearchOperator) {
        updateAdvancedCondition(index) { it.copy(operator = operator) }
    }

    fun updateAdvancedConditionValue(index: Int, value: String) {
        updateAdvancedCondition(index) { it.copy(value = value) }
    }

    fun updateAdvancedConditionIgnoreCase(index: Int, ignoreCase: Boolean) {
        updateAdvancedCondition(index) { it.copy(ignoreCase = ignoreCase) }
    }

    private fun updateAdvancedCondition(
        index: Int,
        transform: (AdvancedSearchCondition) -> AdvancedSearchCondition
    ) {
        advancedSearchConditions.value = advancedSearchConditions.value.mapIndexed { conditionIndex, condition ->
            if (conditionIndex == index) transform(condition) else condition
        }
    }

    private data class SearchInput(
        val keyword: String,
        val advancedEnabled: Boolean,
        val joinMode: AdvancedSearchJoinMode,
        val conditions: List<AdvancedSearchCondition>,
        val lyricSearchEnabled: Boolean
    )
}
