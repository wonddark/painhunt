package com.painhunt.data

import com.painhunt.domain.Bookmark
import com.painhunt.domain.Note
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

class BookmarksRepository(private val client: SupabaseClient) {

    suspend fun getBookmarkedIdeaIds(): Set<String> =
        client.from("bookmarks").select().decodeList<Bookmark>()
            .map { it.ideaId }.toSet()

    suspend fun addBookmark(ideaId: String) {
        client.from("bookmarks").insert(mapOf("idea_id" to ideaId))
    }

    suspend fun removeBookmark(ideaId: String) {
        client.from("bookmarks").delete {
            filter { eq("idea_id", ideaId) }
        }
    }

    suspend fun getNoteForIdea(ideaId: String): Note? =
        client.from("notes").select {
            filter { eq("idea_id", ideaId) }
            limit(1)
        }.decodeList<Note>().firstOrNull()

    suspend fun upsertNote(ideaId: String, content: String, tags: List<String>) {
        @Serializable
        data class NoteUpsert(
            val idea_id: String,
            val content: String,
            val tags: List<String>,
        )
        client.from("notes").upsert(NoteUpsert(ideaId, content, tags)) {
            onConflict = "idea_id"
        }
    }
}
