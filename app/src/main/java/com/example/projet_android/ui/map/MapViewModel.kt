package com.example.projet_android.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.usecase.GetRouteUseCase
import com.example.projet_android.domain.usecase.LoadPoisUseCase
import com.google.android.gms.maps.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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
    private var lastUserLocation: LatLng? = null

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
                        allPois = pois.sortedBy { poi -> poi.name },
                        pois = emptyList(),
                        isRouteSelectionPending = true,
                        draftSelectedPoiIds = emptySet(),
                        visitedPoiIds = emptySet(),
                        score = 0,
                        suggestedPoi = null,
                        selectedPoi = null,
                        route = null,
                        etaEpochMillis = null,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        errorMessage = throwable.message
                            ?: "Impossible de charger les lieux d\u2019int\u00E9r\u00EAt."
                    )
                }
            }
        }
    }

    fun selectPoi(poi: Poi) {
        val state = _uiState.value
        if (state.isRouteSelectionPending || state.pois.none { it.id == poi.id }) return
        _uiState.update {
            it.copy(selectedPoi = poi, route = null, etaEpochMillis = null, errorMessage = null)
        }
    }

    fun toggleDraftPoiSelection(poiId: String) {
        val state = _uiState.value
        if (!state.isRouteSelectionPending || state.allPois.none { it.id == poiId }) return

        val updatedSelection = state.draftSelectedPoiIds.toMutableSet().apply {
            if (!add(poiId)) remove(poiId)
        }
        _uiState.update { it.copy(draftSelectedPoiIds = updatedSelection) }
    }

    fun startDraftRouteSelection() {
        confirmRouteSelection(_uiState.value.draftSelectedPoiIds)
    }

    fun beginRouteSelectionEdit() {
        _uiState.update { state ->
            state.copy(
                isRouteSelectionPending = true,
                draftSelectedPoiIds = state.pois.map { poi -> poi.id }.toSet(),
                selectedPoi = null,
                route = null,
                etaEpochMillis = null,
                errorMessage = null
            )
        }
    }

    fun confirmRouteSelection(selectedPoiIds: Set<String>) {
        if (selectedPoiIds.isEmpty()) return

        val selectedPois = _uiState.value.allPois.filter { poi -> poi.id in selectedPoiIds }
        _uiState.update { state ->
            val updatedState = withSuggestedPoi(
                state.copy(
                    pois = selectedPois,
                    isRouteSelectionPending = false,
                    draftSelectedPoiIds = selectedPoiIds,
                    visitedPoiIds = state.visitedPoiIds.intersect(selectedPoiIds),
                    selectedPoi = state.selectedPoi?.takeIf { it.id in selectedPoiIds },
                    route = null,
                    etaEpochMillis = null,
                    errorMessage = null
                )
            )
            updatedState.copy(
                selectedPoi = updatedState.selectedPoi ?: updatedState.suggestedPoi
            )
        }
    }

    fun loadRoute(currentLocation: LatLng) {
        val selectedPoi = _uiState.value.selectedPoi ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoute = true, errorMessage = null) }
            val result = getRouteUseCase(currentLocation, selectedPoi.toLatLng())
            result.onSuccess { route ->
                val etaMillis = System.currentTimeMillis() + (route.durationSeconds * 1000L).toLong()
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        route = route,
                        etaEpochMillis = etaMillis,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        route = null,
                        etaEpochMillis = null,
                        errorMessage = throwable.message ?: "Impossible de charger l\u2019itin\u00E9raire."
                    )
                }
            }
        }
    }

    fun updateUserLocation(location: LatLng) {
        lastUserLocation = location
        _uiState.update { state ->
            withSuggestedPoi(state)
        }
    }

    fun markCheckpointVisited(poiId: String, goodMatches: Int, confidence: Float) {
        val state = _uiState.value
        if (state.isRouteSelectionPending || state.pois.none { it.id == poiId } || poiId in state.visitedPoiIds) {
            return
        }

        val gainedScore = 100 +
            (confidence.coerceIn(0f, 1f) * 100f).roundToInt() +
            (goodMatches.coerceAtLeast(0) / 5)

        _uiState.update {
            withSuggestedPoi(
                it.copy(
                    visitedPoiIds = it.visitedPoiIds + poiId,
                    score = it.score + gainedScore,
                    errorMessage = null
                )
            )
        }
    }

    private fun withSuggestedPoi(state: MapUiState): MapUiState {
        return state.copy(
            suggestedPoi = computeSuggestedPoi(
                pois = state.pois,
                visitedPoiIds = state.visitedPoiIds,
                userLocation = lastUserLocation
            )
        )
    }

    private fun computeSuggestedPoi(
        pois: List<Poi>,
        visitedPoiIds: Set<String>,
        userLocation: LatLng?
    ): Poi? {
        val candidates = pois.filterNot { it.id in visitedPoiIds }
        if (candidates.isEmpty()) return null
        if (userLocation == null) return candidates.first()

        return candidates.minByOrNull { poi ->
            haversineDistanceMeters(
                fromLat = userLocation.latitude,
                fromLon = userLocation.longitude,
                toLat = poi.latitude,
                toLon = poi.longitude
            )
        }
    }

    private fun haversineDistanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val fromLatRad = Math.toRadians(fromLat)
        val toLatRad = Math.toRadians(toLat)

        val a = sin(dLat / 2).let { it * it } +
            cos(fromLatRad) * cos(toLatRad) * sin(dLon / 2).let { it * it }
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return earthRadius * c
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


