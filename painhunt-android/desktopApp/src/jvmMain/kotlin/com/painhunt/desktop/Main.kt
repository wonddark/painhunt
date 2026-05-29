package com.painhunt.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.ChatRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SourcesRepository
import com.painhunt.data.SupabaseClientProvider

fun main() = application {
    val supabase = SupabaseClientProvider.create(AppConfig.SUPABASE_URL, AppConfig.SUPABASE_ANON_KEY)
    val repos = AppRepositories(
        ideas = IdeasRepository(supabase),
        bookmarks = BookmarksRepository(supabase),
        chat = ChatRepository(supabase),
        sources = SourcesRepository(supabase),
        settings = SettingsRepository(supabase),
        implementations = ImplementationsRepository(supabase),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "PainHunt",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App(repos)
    }
}
