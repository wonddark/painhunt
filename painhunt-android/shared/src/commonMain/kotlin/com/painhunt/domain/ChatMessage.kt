package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)
