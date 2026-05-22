package com.painhunt.data

import com.painhunt.domain.Implementation
import com.painhunt.domain.ImplementationGoal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

class ImplementationsRepository(private val client: SupabaseClient) {

    @Serializable
    private data class GoalsUpdate(val goals: List<ImplementationGoal>)

    suspend fun getAll(): List<Implementation> =
        client.from("implementations").select {
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun getById(id: String): Implementation =
        client.from("implementations").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingle()

    suspend fun getByIdeaId(ideaId: String): Implementation? =
        client.from("implementations").select {
            filter { eq("idea_id", ideaId) }
            limit(1)
        }.decodeList<Implementation>().firstOrNull()

    suspend fun updateGoals(id: String, goals: List<ImplementationGoal>) {
        client.from("implementations").update(GoalsUpdate(goals)) {
            filter { eq("id", id) }
        }
    }

    suspend fun remove(id: String) {
        client.from("implementations").delete {
            filter { eq("id", id) }
        }
    }
}
