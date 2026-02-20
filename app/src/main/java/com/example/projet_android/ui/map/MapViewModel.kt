package com.example.projet_android.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.usecase.GetRouteUseCase
import com.example.projet_android.domain.usecase.LoadPoisUseCase
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val loadPoisUseCase: LoadPoisUseCase,
    private val getRouteUseCase: GetRouteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    fun loadPois() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingPois = true, errorMessage = null)
            }

            val result = loadPoisUseCase()
            result.onSuccess { pois ->
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        pois = pois.sortedBy { poi -> poi.name },
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        errorMessage = throwable.message ?: "Unable to load POI."
                    )
                }
            }
        }
    }

    fun selectPoi(poi: Poi) {
        _uiState.update {
            it.copy(selectedPoi = poi, route = null, errorMessage = null)
        }
    }

    fun loadRoute(currentLocation: LatLng) {
        val selectedPoi = _uiState.value.selectedPoi ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoute = true, errorMessage = null) }
            val result = getRouteUseCase(currentLocation, selectedPoi.toLatLng())
            result.onSuccess { route ->
                _uiState.update { it.copy(isLoadingRoute = false, route = route, errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        route = null,
                        errorMessage = throwable.message ?: "Unable to load route."
                    )
                }
            }
        }
    }

}

class MapViewModelFactory(
    private val loadPoisUseCase: LoadPoisUseCase,
    private val getRouteUseCase: GetRouteUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(
                loadPoisUseCase = loadPoisUseCase,
                getRouteUseCase = getRouteUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
