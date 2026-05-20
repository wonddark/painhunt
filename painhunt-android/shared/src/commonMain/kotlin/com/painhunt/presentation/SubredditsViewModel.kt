package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.SubredditsRepository
import com.painhunt.domain.Subreddit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubredditsUiState(
    val subreddits: List<Subreddit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SubredditsViewModel(
    private val subredditsRepository: SubredditsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubredditsUiState())
    val uiState: StateFlow<SubredditsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                _uiState.update { it.copy(subreddits = subredditsRepository.getAll(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch {
            subredditsRepository.add(name)
            load()
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            subredditsRepository.remove(id)
            load()
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            subredditsRepository.setActive(id, active)
            load()
        }
    }
}
