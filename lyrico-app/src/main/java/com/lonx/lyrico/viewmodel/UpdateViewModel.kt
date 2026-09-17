package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.data.dto.ReleaseInfo
import com.lonx.lyrico.data.model.UpdateCheckResult
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.UpdateRepository
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 更新页的三种状态：正在查、查到了（有无更新都算）、查失败。 */
sealed interface UpdateUiState {
    data object Loading : UpdateUiState

    data class Ready(
        val release: ReleaseInfo,
        val hasUpdate: Boolean
    ) : UpdateUiState

    data class Error(val message: UiMessage) : UpdateUiState
}

class UpdateViewModel(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Loading)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 启动时是否自动检查更新，开关挪到了更新页右上角的设置面板。 */
    val checkUpdateEnabled: StateFlow<Boolean> = settingsRepository.checkUpdateEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var checkJob: Job? = null

    init {
        checkUpdate()
    }

    fun setCheckUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveCheckUpdateEnabled(enabled) }
    }

    fun checkUpdate() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.Loading
            _uiState.value = when (
                val result = updateRepository.fetchLatestRelease(OWNER_ID, REPO_NAME)
            ) {
                is UpdateCheckResult.NewVersion ->
                    UpdateUiState.Ready(result.info, hasUpdate = true)

                is UpdateCheckResult.NoUpdateAvailable -> result.info
                    ?.let { UpdateUiState.Ready(it, hasUpdate = false) }
                    ?: UpdateUiState.Error(UiMessage.StringResource(R.string.update_parse_error))

                is UpdateCheckResult.ApiError -> UpdateUiState.Error(
                    UiMessage.StringResource(R.string.update_api_error, result.code)
                )

                is UpdateCheckResult.NetworkError ->
                    UpdateUiState.Error(UiMessage.StringResource(R.string.update_network_error))

                UpdateCheckResult.ParsingError ->
                    UpdateUiState.Error(UiMessage.StringResource(R.string.update_parse_error))

                UpdateCheckResult.TimeoutError ->
                    UpdateUiState.Error(UiMessage.StringResource(R.string.update_timeout))
            }
        }
    }
}
