package com.example.projet_android.data.repository

import com.example.projet_android.domain.model.Poi

interface PoiRepository {
    suspend fun getPois(): Result<List<Poi>>
}
