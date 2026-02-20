package com.example.projet_android.data.repository

import com.example.projet_android.data.remote.RoutingApiService
import com.example.projet_android.domain.model.RouteResponse
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultRoutingRepository(
    private val routingApiService: RoutingApiService
) : RoutingRepository {

    override suspend fun getRoute(from: LatLng, to: LatLng): Result<RouteResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
                val response = routingApiService.getWalkingRoute(coordinates = coordinates)
                OsrmRouteMapper.toDomain(response)
            }
        }
    }
}
