package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.ImplementationsRepository
import com.painhunt.domain.Implementation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImplementingListUiState(
    val implementations: List<Implementation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ImplementingListViewModel(
    private val implementationsRepository: ImplementationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImplementingListUiState())
    val uiState: StateFlow<ImplementingListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                _uiState.update { it.copy(implementations = implementationsRepository.getAll(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            try {
                implementationsRepository.remove(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
