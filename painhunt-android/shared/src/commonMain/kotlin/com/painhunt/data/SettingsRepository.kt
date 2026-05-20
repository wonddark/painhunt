package com.painhunt.data

import com.painhunt.domain.AppSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SettingsRepository(private val client: SupabaseClient) {

    suspend fun get(): AppSettings =
        client.from("settings").select().decodeSingle()

    suspend fun update(id: String, model: String, minUpvotes: Int, scraperBaseUrl: String) {
        client.from("settings").update(
            mapOf(
                "ollama_model" to model,
                "min_upvotes_threshold" to minUpvotes,
                "scraper_base_url" to scraperBaseUrl,
            )
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun updateApiKey(id: String, apiKey: String) {
        client.from("settings").update(mapOf("ollama_api_key" to apiKey)) {
            filter { eq("id", id) }
        }
    }
}
