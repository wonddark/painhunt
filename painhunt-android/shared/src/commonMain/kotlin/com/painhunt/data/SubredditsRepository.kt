package com.painhunt.data

import com.painhunt.domain.Subreddit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SubredditsRepository(private val client: SupabaseClient) {

    suspend fun getAll(): List<Subreddit> =
        client.from("subreddits").select {
            order("added_at", Order.ASCENDING)
        }.decodeList()

    suspend fun add(name: String) {
        client.from("subreddits").insert(mapOf("name" to name.removePrefix("r/").trim()))
    }

    suspend fun remove(id: String) {
        client.from("subreddits").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        client.from("subreddits").update(mapOf("active" to active)) {
            filter { eq("id", id) }
        }
    }
}
