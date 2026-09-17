package com.lonx.lyrico.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDTO(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String,
    val published_at: String? = null,
    val assets: List<GitHubReleaseAssetDTO> = emptyList()
)

@Serializable
data class GitHubReleaseAssetDTO(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L
)
