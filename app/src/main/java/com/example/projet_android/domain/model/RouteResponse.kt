package com.example.projet_android.domain.model

import com.google.android.gms.maps.model.LatLng

data class RouteResponse(
    val polylinePoints: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double
)
