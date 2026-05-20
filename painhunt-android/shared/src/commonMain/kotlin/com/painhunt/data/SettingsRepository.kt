package com.painhunt.data

import com.painhunt.domain.AppSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SettingsRepository(private val client: SupabaseClient) {

    @Serializable
    private data class SettingsUpdate(
        @SerialName("ollama_model") val ollamaModel: String,
        @SerialName("min_upvotes_threshold") val minUpvotesThreshold: Int,
        @SerialName("scraper_base_url") val scraperBaseUrl: String,
    )

    @Serializable
    private data class ApiKeyUpdate(
        @SerialName("ollama_api_key") val ollamaApiKey: String,
    )

    suspend fun get(): AppSettings =
        client.from("settings").select().decodeSingle()

    suspend fun update(id: String, model: String, minUpvotes: Int, scraperBaseUrl: String) {
        client.from("settings").update(SettingsUpdate(model, minUpvotes, scraperBaseUrl)) {
            filter { eq("id", id) }
        }
    }

    suspend fun updateApiKey(id: String, apiKey: String) {
        client.from("settings").update(ApiKeyUpdate(apiKey)) {
            filter { eq("id", id) }
        }
    }
}
