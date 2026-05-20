package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.SettingsRepository
import com.painhunt.domain.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                _uiState.update { it.copy(settings = settingsRepository.get(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun save(model: String, minUpvotes: Int, apiKey: String, scraperUrl: String) {
        val id = _uiState.value.settings?.id ?: return
        viewModelScope.launch {
            try {
                settingsRepository.update(id, model, minUpvotes, scraperUrl)
                settingsRepository.updateApiKey(id, apiKey)
                _uiState.update { it.copy(isSaved = true) }
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearSaved() = _uiState.update { it.copy(isSaved = false) }
}
