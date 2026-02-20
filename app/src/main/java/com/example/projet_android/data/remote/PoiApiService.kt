package com.example.projet_android.data.remote

import com.example.projet_android.data.remote.dto.PoiDto
import retrofit2.http.GET

interface PoiApiService {
    @GET("pois")
    suspend fun getPois(): List<PoiDto>
}
