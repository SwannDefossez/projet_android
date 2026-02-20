package com.example.projet_android.domain.usecase

import com.example.projet_android.data.repository.RoutingRepository
import com.example.projet_android.domain.model.RouteResponse
import com.google.android.gms.maps.model.LatLng

class GetRouteUseCase(private val routingRepository: RoutingRepository) {
    suspend operator fun invoke(from: LatLng, to: LatLng): Result<RouteResponse> {
        return routingRepository.getRoute(from, to)
    }
}
