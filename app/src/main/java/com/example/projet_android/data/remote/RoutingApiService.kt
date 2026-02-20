package com.example.projet_android.data.remote

import com.example.projet_android.data.remote.dto.OsrmRouteResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RoutingApiService {
    @GET("route/v1/walking/{coordinates}")
    suspend fun getWalkingRoute(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("alternatives") alternatives: Boolean = false
    ): OsrmRouteResponse
}
