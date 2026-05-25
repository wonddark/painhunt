package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val role: ChatRole,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)
