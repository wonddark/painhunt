package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.ChatRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.domain.ChatMessage
import com.painhunt.domain.ChatRole
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class IdeaChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val error: String? = null,
)

class IdeaChatViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    @Serializable
    private data class ChatMessagePayload(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val ideaId: String,
        val messages: List<ChatMessagePayload>,
    )

    private val httpClient = HttpClient()

    private val _uiState = MutableStateFlow(IdeaChatUiState())
    val uiState: StateFlow<IdeaChatUiState> = _uiState.asStateFlow()

    fun load(ideaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, error = null) }
            try {
                val messages = chatRepository.getMessagesForIdea(ideaId)
                _uiState.update { it.copy(messages = messages, isLoadingHistory = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingHistory = false, error = e.message) }
            }
        }
    }

    fun send(ideaId: String, text: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            try {
                val userMsg = chatRepository.insertMessage(ideaId, ChatRole.USER, text)
                val updatedMessages = _uiState.value.messages + userMsg
                _uiState.update {
                    it.copy(messages = updatedMessages, isStreaming = true, streamingText = "", error = null)
                }

                val payloads = updatedMessages.map { ChatMessagePayload(it.role.name.lowercase(), it.content) }
                val requestBody = Json.encodeToString(ChatRequest(ideaId, payloads))
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl

                var fullText = ""
                httpClient.preparePost("$scraperBaseUrl/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        val payload = line.removePrefix("data: ").trim()
                        if (payload == "[DONE]") break
                        val json = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: continue
                        val token = json.jsonObject["message"]
                            ?.jsonObject?.get("content")
                            ?.jsonPrimitive?.content
                            ?: continue
                        if (token.isEmpty()) continue
                        fullText += token
                        _uiState.update { it.copy(streamingText = fullText) }
                    }
                }

                val assistantMsg = chatRepository.insertMessage(ideaId, ChatRole.ASSISTANT, fullText)
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMsg,
                        streamingText = "",
                        isStreaming = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStreaming = false, error = "Failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
