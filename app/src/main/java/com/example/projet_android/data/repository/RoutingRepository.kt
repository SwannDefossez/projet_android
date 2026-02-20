package com.example.projet_android.data.repository

import com.example.projet_android.domain.model.RouteResponse
import com.google.android.gms.maps.model.LatLng

interface RoutingRepository {
    suspend fun getRoute(from: LatLng, to: LatLng): Result<RouteResponse>
}
