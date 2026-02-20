package com.example.projet_android.data.repository

import com.example.projet_android.data.remote.dto.OsrmRouteResponse
import com.example.projet_android.domain.model.RouteResponse
import com.google.android.gms.maps.model.LatLng

object OsrmRouteMapper {
    fun toDomain(response: OsrmRouteResponse): RouteResponse {
        val firstRoute = response.routes.firstOrNull()
            ?: throw IllegalStateException("No route returned by OSRM")

        val points = firstRoute.geometry.coordinates.mapNotNull { coordinate ->
            if (coordinate.size < 2) return@mapNotNull null
            LatLng(coordinate[1], coordinate[0])
        }

        if (points.isEmpty()) {
            throw IllegalStateException("OSRM route geometry is empty")
        }

        return RouteResponse(
            polylinePoints = points,
            distanceMeters = firstRoute.distance,
            durationSeconds = firstRoute.duration
        )
    }
}
