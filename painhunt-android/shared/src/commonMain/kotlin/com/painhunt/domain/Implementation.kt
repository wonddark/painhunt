package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Implementation(
    val id: String,
    @SerialName("idea_id") val ideaId: String,
    val concept: String,
    val description: String,
    val goals: List<ImplementationGoal>,
    @SerialName("created_at") val createdAt: String,
)
