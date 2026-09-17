package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.UpdateCheckResult

interface UpdateRepository {
    suspend fun checkForUpdate(
        owner: String,
        repo: String
    ): UpdateCheckResult

    /**
     * 拉取最新 Release，不论是否比当前版本新。
     * 更新页要展示当前版本的更新日志，所以不能用 [checkForUpdate] 的“无更新”分支。
     */
    suspend fun fetchLatestRelease(
        owner: String,
        repo: String
    ): UpdateCheckResult
}
