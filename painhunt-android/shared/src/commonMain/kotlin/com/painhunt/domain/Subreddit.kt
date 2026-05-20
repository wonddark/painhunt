package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Subreddit(
    val id: String,
    val name: String,
    val active: Boolean,
    @SerialName("added_at") val addedAt: String,
)
