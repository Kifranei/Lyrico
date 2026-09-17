package com.lonx.lyrico.ui.components.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.data.repository.SettingsRepository
import org.koin.compose.koinInject

/** The configured artist poster folders, plus the revision that changes when they are refreshed. */
internal data class ArtistPosterSource(
    val folders: List<String> = emptyList(),
    val revision: Long = 0L
)

/**
 * Reads the artist poster settings for the composables that render artist artwork.
 *
 * Only artist artwork needs them, so `enabled = false` keeps ordinary covers free of the
 * subscription and lets them be requested without waiting for the folder list.
 */
@Composable
internal fun rememberArtistPosterSource(enabled: Boolean = true): ArtistPosterSource {
    if (!enabled) return ArtistPosterSource()
    val settings: SettingsRepository = koinInject()
    val folders by settings.artistPosterFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val revision by settings.artistPosterRevision.collectAsStateWithLifecycle(initialValue = 0L)
    return ArtistPosterSource(folders = folders, revision = revision)
}
