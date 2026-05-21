package com.painhunt.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.IdeasRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SortField
import com.painhunt.domain.Idea
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class FeedUiState(
    val ideas: List<Idea> = emptyList(),
    val isLoading: Boolean = false,
    val isScraping: Boolean = false,
    val isUploading: Boolean = false,
    val sortBy: SortField = SortField.ScrapedAt,
    val selectedCategory: String? = null,
    val error: String? = null,
    val scrapeResult: String? = null,
)

class FeedViewModel(
    private val ideasRepository: IdeasRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val httpClient = HttpClient()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadIdeas()
    }

    fun loadIdeas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val ideas = ideasRepository.getIdeas(
                    sortBy = _uiState.value.sortBy,
                    category = _uiState.value.selectedCategory,
                )
                _uiState.update { it.copy(ideas = ideas, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setSortBy(sortBy: SortField) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadIdeas()
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadIdeas()
    }

    fun triggerScrape() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScraping = true, scrapeResult = null) }
            try {
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val response = httpClient.post("$scraperBaseUrl/scrape")
                val body = response.bodyAsText()
                _uiState.update { it.copy(isScraping = false, scrapeResult = body) }
                loadIdeas()
            } catch (e: Exception) {
                _uiState.update { it.copy(isScraping = false, error = "Scraper offline: ${e.message}") }
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, scrapeResult = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IOException("Cannot read file")
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val response = httpClient.post("$scraperBaseUrl/scrape/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(bytes)
                }
                if (!response.status.isSuccess()) {
                    throw IOException("Server error ${response.status.value}: ${response.bodyAsText()}")
                }
                val body = response.bodyAsText()
                _uiState.update { it.copy(isUploading = false, scrapeResult = body) }
                loadIdeas()
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, error = "Upload failed: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
