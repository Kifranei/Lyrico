package com.lonx.lyrico.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * 多选管理器，用于跨页面的选择的歌曲列表数据共享
 */
class SharedSelectionManager {

    // 基于 URI 的多选，数据类型为String
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    // 基于 mediaId 的多选，数据类型为Long
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // 设置选中的 URI
    fun setUris(uris: Set<String>) {
        _selectedUris.value = uris
    }

    // 设置选中的 ID
    fun setIds(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    // 操作完成后，清除数据，防止内存污染
    fun clearAll() {
        _selectedUris.value = emptySet()
        _selectedIds.value = emptySet()
    }
}