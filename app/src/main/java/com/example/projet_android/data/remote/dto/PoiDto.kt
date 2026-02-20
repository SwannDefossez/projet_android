package com.example.projet_android.data.remote.dto

import com.example.projet_android.domain.model.Poi

data class PoiDto(
    val id: String,
    val name: String,
    val address: String? = "",
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val referenceImageAssetPath: String,
    val audioResName: String,
    val orbMatchThreshold: Int
) {
    fun toDomain(): Poi {
        return Poi(
            id = id,
            name = name,
            address = address.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            description = description,
            referenceImageAssetPath = referenceImageAssetPath,
            audioResName = audioResName,
            orbMatchThreshold = orbMatchThreshold
        )
    }
}
