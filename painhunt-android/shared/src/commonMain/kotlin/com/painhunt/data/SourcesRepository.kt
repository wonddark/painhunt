package com.painhunt.data

import com.painhunt.domain.Subreddit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SourcesRepository(private val client: SupabaseClient) {

    @Serializable
    private data class SubredditInsert(val name: String)

    @Serializable
    private data class ActiveUpdate(val active: Boolean)

    @Serializable
    private data class NameUpdate(val name: String)

    suspend fun getAll(): List<Subreddit> =
        client.from("subreddits").select {
            order("added_at", Order.ASCENDING)
        }.decodeList()

    suspend fun add(name: String) {
        client.from("subreddits").insert(SubredditInsert(name.removePrefix("r/").trim()))
    }

    suspend fun remove(id: String) {
        client.from("subreddits").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        client.from("subreddits").update(ActiveUpdate(active)) {
            filter { eq("id", id) }
        }
    }

    suspend fun rename(id: String, name: String) {
        client.from("subreddits").update(NameUpdate(name.removePrefix("r/").trim())) {
            filter { eq("id", id) }
        }
    }
}
