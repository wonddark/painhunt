package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    @SerialName("saved_at") val savedAt: String,
)
