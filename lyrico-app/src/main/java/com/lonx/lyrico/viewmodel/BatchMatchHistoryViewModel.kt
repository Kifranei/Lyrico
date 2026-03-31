package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.repository.BatchMatchHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class BatchMatchHistoryUiState(
    val historyList: List<BatchMatchHistory> = emptyList(),
    val allRecords: List<BatchMatchRecordEntity> = emptyList(),
    val isLoading: Boolean = false
)

class BatchMatchHistoryViewModel(
    private val repository: BatchMatchHistoryRepository
) : ViewModel() {

    private val historyIdFlow = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(BatchMatchHistoryUiState(isLoading = true))
    val uiState: StateFlow<BatchMatchHistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistoryList()
        observeRecords()
    }

    /**
     * 详情页调用：告诉 ViewModel 加载哪条历史的明细
     */
    fun loadHistory(historyId: Long) {
        historyIdFlow.value = historyId
    }

    /**
     * 监听所有历史记录（用于 BatchMatchHistoryScreen 列表页）
     */
    private fun observeHistoryList() {
        viewModelScope.launch {
            repository.getAllHistory().collect { list ->
                _uiState.update {
                    it.copy(
                        historyList = list,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 监听当前选中历史的明细记录（用于 BatchMatchHistoryDetailScreen 详情页）
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecords() {
        viewModelScope.launch {
            historyIdFlow
                .filterNotNull()
                .flatMapLatest { historyId ->
                    repository.getRecordsByHistoryId(historyId)
                }
                .collect { records ->
                    _uiState.update {
                        it.copy(allRecords = records)
                    }
                }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
            // 删除时如果正好是当前详情页的数据，可以清空 detail
            if (historyIdFlow.value == id) {
                historyIdFlow.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            historyIdFlow.value = null
        }
    }

    suspend fun getHistoryById(id: Long): BatchMatchHistory? {
        return repository.getHistoryById(id)
    }
}
