package com.svce.attendance.services

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions


object SupabaseConfig {
    // Use your actual Supabase project credentials below
    const val SUPABASE_URL = "https://srjqlzigskqhckftxvxk.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNyanFsemlnc2txaGNrZnR4dnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTUyNTEzMDcsImV4cCI6MjA3MDgyNzMwN30.d7XXsULRXu80rF-JYT5KBgxJhFABqaXGdOKjd4apQrU"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
        install(Auth)
        install(Storage)
        install(Functions)
    }
}