package com.example.ui.screens

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

val supabase = createSupabaseClient(
    supabaseUrl = "https://ixdptpslsjfxozehesgw.supabase.co",
    supabaseKey = "sb_publishable_zOx_v5RG8-viMhpahdz-CA_Ju8HFTbr"
) {
    install(Postgrest)
    install(Auth)
}
