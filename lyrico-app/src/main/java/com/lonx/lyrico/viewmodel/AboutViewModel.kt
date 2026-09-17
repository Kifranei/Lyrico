package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.data.dto.ContributorInfo
import com.lonx.lyrico.data.model.AboutBgEffect
import com.lonx.lyrico.data.repository.GhContributorRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.HyperOsDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


sealed interface UiError {
    data object LoadFailed : UiError
    data class Message(val text: String) : UiError
}
class AboutViewModel(
    private val settingsRepository: SettingsRepository,
    private val contributorRepository: GhContributorRepository
) : ViewModel() {

    // 首帧就用设备默认值，免得流光先关后开闪一下
    val aboutBgEffect: StateFlow<AboutBgEffect> =
        settingsRepository.aboutBgEffect
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                HyperOsDetector.defaultAboutBgEffect()
            )

    private val _contributors = MutableStateFlow<List<ContributorInfo>>(emptyList())
    val contributors: StateFlow<List<ContributorInfo>> = _contributors

    private val _loadingContributors = MutableStateFlow(false)
    val loadingContributors: StateFlow<Boolean> = _loadingContributors

    private val _contributorsError = MutableStateFlow<UiError?>(null)
    val contributorsError: StateFlow<UiError?> = _contributorsError
    init {
        loadContributors()
    }
    fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun loadContributors() {
        viewModelScope.launch {
            _loadingContributors.value = true
            _contributorsError.value = null

            val result = contributorRepository.getContributors(
                owner = OWNER_ID,
                repo = REPO_NAME
            )

            result
                .onSuccess {
                    _contributors.value = it
                }
                .onFailure {
                    _contributorsError.value =
                        it.message?.let { msg -> UiError.Message(msg) }
                            ?: UiError.LoadFailed
                }

            _loadingContributors.value = false
        }
    }
}