package com.example.projet_android

import com.example.projet_android.data.repository.PoiRepository
import com.example.projet_android.data.repository.RoutingRepository
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RouteResponse
import com.example.projet_android.domain.usecase.GetRouteUseCase
import com.example.projet_android.domain.usecase.LoadPoisUseCase
import com.example.projet_android.ui.map.MapViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelProgressionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `marks checkpoint, updates score, suggests next and computes eta`() = runTest {
        val pois = listOf(
            samplePoi(id = "poi_a", latitude = 49.8350, longitude = 3.2980, name = "A"),
            samplePoi(id = "poi_b", latitude = 49.8370, longitude = 3.3000, name = "B")
        )
        val route = RouteResponse(
            polylinePoints = listOf(LatLng(49.8350, 3.2980), LatLng(49.8370, 3.3000)),
            distanceMeters = 850.0,
            durationSeconds = 620.0
        )
        val viewModel = MapViewModel(
            loadPoisUseCase = LoadPoisUseCase(FakePoiRepository(pois)),
            getRouteUseCase = GetRouteUseCase(FakeRoutingRepository(route))
        )

        viewModel.loadPois()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRouteSelectionPending)
        viewModel.confirmRouteSelection(pois.map { it.id }.toSet())
        assertEquals("poi_a", viewModel.uiState.value.selectedPoi?.id)
        viewModel.updateUserLocation(LatLng(49.8371, 3.3001))
        val firstSuggestion = viewModel.uiState.value.suggestedPoi
        assertEquals("poi_b", firstSuggestion?.id)

        viewModel.selectPoi(firstSuggestion!!)
        viewModel.loadRoute(LatLng(49.8371, 3.3001))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.etaEpochMillis)

        viewModel.markCheckpointVisited(
            poiId = firstSuggestion.id,
            goodMatches = 22,
            confidence = 0.75f
        )
        val state = viewModel.uiState.value
        assertTrue("poi_b" in state.visitedPoiIds)
        assertTrue(state.score > 0)
        assertEquals("poi_a", state.suggestedPoi?.id)
    }

    @Test
    fun `does not double count score for already visited checkpoint`() = runTest {
        val poi = samplePoi(id = "poi_unique", latitude = 49.8350, longitude = 3.2980, name = "Unique")
        val viewModel = MapViewModel(
            loadPoisUseCase = LoadPoisUseCase(FakePoiRepository(listOf(poi))),
            getRouteUseCase = GetRouteUseCase(FakeRoutingRepository(emptyRoute()))
        )

        viewModel.loadPois()
        advanceUntilIdle()
        viewModel.confirmRouteSelection(setOf(poi.id))
        viewModel.markCheckpointVisited(poiId = poi.id, goodMatches = 10, confidence = 0.5f)
        val firstScore = viewModel.uiState.value.score
        viewModel.markCheckpointVisited(poiId = poi.id, goodMatches = 40, confidence = 1f)

        assertEquals(firstScore, viewModel.uiState.value.score)
        assertEquals(1, viewModel.uiState.value.visitedPoiIds.size)
    }

    private fun samplePoi(
        id: String,
        latitude: Double,
        longitude: Double,
        name: String
    ): Poi {
        return Poi(
            id = id,
            name = name,
            address = "address",
            latitude = latitude,
            longitude = longitude,
            description = "desc",
            referenceImageAssetPath = "reference_images/$id.png",
            audioResName = "guide_$id",
            orbMatchThreshold = 18
        )
    }

    private fun emptyRoute(): RouteResponse {
        return RouteResponse(
            polylinePoints = listOf(LatLng(49.8350, 3.2980), LatLng(49.8351, 3.2981)),
            distanceMeters = 10.0,
            durationSeconds = 20.0
        )
    }

    private class FakePoiRepository(
        private val pois: List<Poi>
    ) : PoiRepository {
        override suspend fun getPois(): Result<List<Poi>> = Result.success(pois)
    }

    private class FakeRoutingRepository(
        private val routeResponse: RouteResponse
    ) : RoutingRepository {
        override suspend fun getRoute(from: LatLng, to: LatLng): Result<RouteResponse> {
            return Result.success(routeResponse)
        }
    }
}
