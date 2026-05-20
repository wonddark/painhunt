package com.painhunt.data

import com.painhunt.domain.Idea
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

enum class SortField { ScrapedAt, Relevance }

class IdeasRepository(private val client: SupabaseClient) {

    suspend fun getIdeas(
        sortBy: SortField = SortField.ScrapedAt,
        category: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<Idea> {
        val column = when (sortBy) {
            SortField.ScrapedAt -> "scraped_at"
            SortField.Relevance -> "ai_relevance_score"
        }
        return client.from("ideas").select {
            order(column, Order.DESCENDING)
            if (category != null) filter { eq("ai_category", category) }
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList()
    }

    suspend fun getIdeaById(id: String): Idea =
        client.from("ideas").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingle()
}
