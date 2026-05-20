package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val content: String,
    val tags: List<String>,
    @SerialName("updated_at") val updatedAt: String,
)
