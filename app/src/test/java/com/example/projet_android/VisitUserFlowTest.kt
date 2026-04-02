package com.example.projet_android

import com.example.projet_android.data.repository.PoiRepository
import com.example.projet_android.data.repository.RecognitionRepository
import com.example.projet_android.data.repository.RoutingRepository
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RecognitionResult
import com.example.projet_android.domain.model.RouteResponse
import com.example.projet_android.domain.usecase.GetRouteUseCase
import com.example.projet_android.domain.usecase.LoadPoisUseCase
import com.example.projet_android.domain.usecase.MatchPoiUseCase
import com.example.projet_android.ui.camera.CameraViewModel
import com.example.projet_android.ui.map.MapViewModel
import com.google.android.gms.maps.model.LatLng
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitUserFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `full visit flow validates checkpoint and advances suggested route`() = runTest {
        val poiStart = poi("poi_start", "Start", 49.8367, 3.2994)
        val poiNext = poi("poi_next", "Next", 49.8400, 3.3050)

        val mapViewModel = MapViewModel(
            loadPoisUseCase = LoadPoisUseCase(FakePoiRepository(listOf(poiStart, poiNext))),
            getRouteUseCase = GetRouteUseCase(FakeRoutingRepository())
        )
        mapViewModel.loadPois()
        advanceUntilIdle()
        mapViewModel.confirmRouteSelection(setOf(poiStart.id, poiNext.id))
        mapViewModel.updateUserLocation(LatLng(poiStart.latitude, poiStart.longitude))
        mapViewModel.selectPoi(poiStart)
        mapViewModel.loadRoute(LatLng(poiStart.latitude, poiStart.longitude))
        advanceUntilIdle()

        val cameraViewModel = CameraViewModel(
            matchPoiUseCase = MatchPoiUseCase(
                FakeRecognitionRepository(
                    RecognitionResult(
                        poiId = poiStart.id,
                        goodMatches = 28,
                        isMatch = true,
                        confidence = 0.9f
                    )
                )
            )
        )

        val tempCapture = File.createTempFile("capture_visit_flow", ".jpg").apply {
            writeText("fake")
        }
        cameraViewModel.analyzePhoto(poiStart, tempCapture.absolutePath)
        advanceUntilIdle()

        val recognition = cameraViewModel.uiState.value.result
        assertTrue(recognition?.isMatch == true)
        assertFalse(tempCapture.exists())

        mapViewModel.markCheckpointVisited(
            poiId = recognition!!.poiId,
            goodMatches = recognition.goodMatches,
            confidence = recognition.confidence
        )

        val state = mapViewModel.uiState.value
        assertEquals(1, state.visitedPoiIds.size)
        assertTrue(state.score > 0)
        assertEquals(poiNext.id, state.suggestedPoi?.id)
    }

    private fun poi(id: String, name: String, latitude: Double, longitude: Double): Poi {
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

    private class FakePoiRepository(private val pois: List<Poi>) : PoiRepository {
        override suspend fun getPois(): Result<List<Poi>> = Result.success(pois)
    }

    private class FakeRoutingRepository : RoutingRepository {
        override suspend fun getRoute(from: LatLng, to: LatLng): Result<RouteResponse> {
            return Result.success(
                RouteResponse(
                    polylinePoints = listOf(from, to),
                    distanceMeters = 150.0,
                    durationSeconds = 120.0
                )
            )
        }
    }

    private class FakeRecognitionRepository(
        private val result: RecognitionResult
    ) : RecognitionRepository {
        override suspend fun matchCapturedImage(poi: Poi, imagePath: String): Result<RecognitionResult> {
            return Result.success(result)
        }
    }
}
