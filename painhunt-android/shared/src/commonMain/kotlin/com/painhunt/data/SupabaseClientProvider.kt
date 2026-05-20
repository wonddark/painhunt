package com.painhunt.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseClient =
        createSupabaseClient(supabaseUrl, supabaseAnonKey) {
            install(Postgrest)
        }
}
