package com.painhunt.data

import com.painhunt.domain.ChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

class ChatRepository(private val client: SupabaseClient) {

    suspend fun getMessagesForIdea(ideaId: String): List<ChatMessage> =
        client.from("chat_messages").select {
            filter { eq("idea_id", ideaId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun insertMessage(ideaId: String, role: String, content: String): ChatMessage {
        @Serializable
        data class Insert(val idea_id: String, val role: String, val content: String)
        client.from("chat_messages").insert(Insert(ideaId, role, content))
        return client.from("chat_messages").select {
            filter { eq("idea_id", ideaId) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeSingle()
    }
}
