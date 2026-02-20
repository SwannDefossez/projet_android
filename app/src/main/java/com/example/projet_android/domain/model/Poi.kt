package com.example.projet_android.domain.model

import com.google.android.gms.maps.model.LatLng

data class Poi(
    val id: String,
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val referenceImageAssetPath: String,
    val audioResName: String,
    val orbMatchThreshold: Int
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}
