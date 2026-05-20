package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val id: String,
    @SerialName("ollama_api_key") val ollamaApiKey: String,
    @SerialName("ollama_model") val ollamaModel: String,
    @SerialName("min_upvotes_threshold") val minUpvotesThreshold: Int,
    @SerialName("scraper_base_url") val scraperBaseUrl: String,
)
