package com.example.projet_android.ui.map

import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RouteResponse

data class MapUiState(
    val isLoadingPois: Boolean = false,
    val isLoadingRoute: Boolean = false,
    val pois: List<Poi> = emptyList(),
    val selectedPoi: Poi? = null,
    val route: RouteResponse? = null,
    val errorMessage: String? = null
)
