package com.example.projet_android.data.remote.dto

data class OsrmRouteResponse(
    val routes: List<OsrmRouteDto> = emptyList()
)

data class OsrmRouteDto(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometryDto
)

data class OsrmGeometryDto(
    val coordinates: List<List<Double>> = emptyList()
)
