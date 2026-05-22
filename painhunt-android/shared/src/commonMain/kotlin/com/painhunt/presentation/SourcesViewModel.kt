package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.SourcesRepository
import com.painhunt.domain.Subreddit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourcesUiState(
    val sources: List<Subreddit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SourcesViewModel(
    private val sourcesRepository: SourcesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                _uiState.update { it.copy(sources = sourcesRepository.getAll(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch {
            try {
                sourcesRepository.add(name)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            try {
                sourcesRepository.remove(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            try {
                sourcesRepository.setActive(id, active)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            try {
                sourcesRepository.rename(id, name)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
