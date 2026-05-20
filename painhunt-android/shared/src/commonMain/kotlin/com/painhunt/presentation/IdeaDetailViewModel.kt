package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.domain.Idea
import com.painhunt.domain.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeaDetailUiState(
    val idea: Idea? = null,
    val isBookmarked: Boolean = false,
    val note: Note? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class IdeaDetailViewModel(
    private val ideasRepository: IdeasRepository,
    private val bookmarksRepository: BookmarksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeaDetailUiState())
    val uiState: StateFlow<IdeaDetailUiState> = _uiState.asStateFlow()

    fun load(ideaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val idea = ideasRepository.getIdeaById(ideaId)
                val bookmarkedIds = bookmarksRepository.getBookmarkedIdeaIds()
                val note = bookmarksRepository.getNoteForIdea(ideaId)
                _uiState.update { it.copy(idea = idea, isBookmarked = ideaId in bookmarkedIds, note = note, isLoading = false) }
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
}
