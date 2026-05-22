package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.domain.Idea
import com.painhunt.domain.Note
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class IdeaDetailUiState(
    val idea: Idea? = null,
    val isBookmarked: Boolean = false,
    val note: Note? = null,
    val isLoading: Boolean = false,
    val isImplementing: Boolean = false,
    val implementationId: String? = null,
    val isStartingImplementation: Boolean = false,
    val error: String? = null,
)

class IdeaDetailViewModel(
    private val ideasRepository: IdeasRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val settingsRepository: SettingsRepository,
    private val implementationsRepository: ImplementationsRepository,
) : ViewModel() {

    @Serializable
    private data class ImplementRequest(
        val ideaId: String,
        val title: String,
        val summary: String,
        val bodyExcerpt: String,
    )

    private val httpClient = HttpClient()

    private val _uiState = MutableStateFlow(IdeaDetailUiState())
    val uiState: StateFlow<IdeaDetailUiState> = _uiState.asStateFlow()

    fun load(ideaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val idea = ideasRepository.getIdeaById(ideaId)
                val bookmarkedIds = bookmarksRepository.getBookmarkedIdeaIds()
                val note = bookmarksRepository.getNoteForIdea(ideaId)
                val existing = implementationsRepository.getByIdeaId(ideaId)
                _uiState.update {
                    it.copy(
                        idea = idea,
                        isBookmarked = ideaId in bookmarkedIds,
                        note = note,
                        isLoading = false,
                        isImplementing = existing != null,
                        implementationId = existing?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleBookmark() {
        val ideaId = _uiState.value.idea?.id ?: return
        viewModelScope.launch {
            if (_uiState.value.isBookmarked) {
                bookmarksRepository.removeBookmark(ideaId)
                _uiState.update { it.copy(isBookmarked = false) }
            } else {
                bookmarksRepository.addBookmark(ideaId)
                _uiState.update { it.copy(isBookmarked = true) }
            }
        }
    }

    fun saveNote(content: String, tags: List<String>) {
        val ideaId = _uiState.value.idea?.id ?: return
        viewModelScope.launch {
            bookmarksRepository.upsertNote(ideaId, content, tags)
        }
    }

    fun startImplementing() {
        val idea = _uiState.value.idea ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingImplementation = true, error = null) }
            try {
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val requestBody = Json.encodeToString(
                    ImplementRequest(
                        ideaId = idea.id,
                        title = idea.title,
                        summary = idea.aiSummary,
                        bodyExcerpt = idea.bodyExcerpt ?: "",
                    )
                )
                val response = httpClient.post("$scraperBaseUrl/implement") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (!response.status.isSuccess()) {
                    throw Exception("Server error ${response.status.value}: ${response.bodyAsText()}")
                }
                val existing = implementationsRepository.getByIdeaId(idea.id)
                _uiState.update {
                    it.copy(
                        isStartingImplementation = false,
                        isImplementing = existing != null,
                        implementationId = existing?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStartingImplementation = false, error = "Failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
