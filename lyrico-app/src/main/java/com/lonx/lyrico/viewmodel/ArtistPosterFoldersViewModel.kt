package com.lonx.lyrico.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.SafDocuments
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.utils.coil.ArtistPosterMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArtistPosterFile(val uri: String, val name: String, val lastModified: Long)

data class ArtistPosterFolder(
    val uri: String,
    val path: String,
    val posters: List<ArtistPosterFile>?,
    /** How many posters in this folder are named after an artist of the library. */
    val matchedPosterCount: Int = 0,
    val name: String = path.substringAfterLast('/').ifBlank { path }
)

data class ArtistPosterFoldersUiState(
    val folders: List<ArtistPosterFolder> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val revision: Long = 0L
)

class ArtistPosterFoldersViewModel(
    private val settings: SettingsRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val application: Application
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtistPosterFoldersUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.artistPosterFolders,
                settings.artistPosterRevision,
                libraryIndexRepository.observeArtists()
            ) { folders, revision, artists -> Triple(folders, revision, artists) }
                .collectLatest { (folders, revision, artists) ->
                    _uiState.update { it.copy(isLoading = true) }
                    val artistNames = artists.map { it.name }
                    val entries = withContext(Dispatchers.IO) {
                        folders.map { folder ->
                            val uri = folder.toUri()
                            val posters = readPosters(uri)
                            ArtistPosterFolder(
                                uri = folder,
                                path = UriUtils.getFileAbsolutePath(application, uri)
                                    ?: Uri.decode(uri.lastPathSegment ?: folder),
                                posters = posters,
                                matchedPosterCount = countMatchedPosters(posters, artistNames)
                            )
                        }
                    }
                    _uiState.update { it.copy(folders = entries, isLoading = false, revision = revision) }
                }
        }
    }

    /**
     * Posters are matched by file name, so a folder full of posters that matches no artist of the
     * library means the names do not line up - surface that instead of silently showing nothing.
     */
    private fun countMatchedPosters(posters: List<ArtistPosterFile>?, artistNames: List<String>): Int =
        ArtistPosterMatcher.countMatchedFiles(posters.orEmpty().map { it.name }, artistNames)

    /** `null` when the folder cannot be queried any more (deleted or access revoked). */
    private fun readPosters(uri: Uri): List<ArtistPosterFile>? =
        SafDocuments.children(application, uri)
            ?.filterNot { it.isDirectory }
            ?.filter { ArtistPosterMatcher.isPosterFile(it.displayName) }
            ?.map { ArtistPosterFile(uri = it.uri.toString(), name = it.displayName, lastModified = it.lastModified) }
            ?.sortedBy { it.name.lowercase() }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                _uiState.update { it.copy(error = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(error = true) }
            }
        }
    }

    fun addFolder(uri: Uri) = update {
        settings.addArtistPosterFolder(uri.toString())
        settings.refreshArtistPosters()
    }

    fun removeFolder(uri: String) = update {
        // Grants can be shared with the music library; removing a poster folder only removes its registration.
        settings.removeArtistPosterFolder(uri)
    }

    fun refresh() = update { settings.refreshArtistPosters() }
}
