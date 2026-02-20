package com.example.projet_android

import com.example.projet_android.data.local.PoiLocalDataSource
import com.example.projet_android.data.remote.PoiApiService
import com.example.projet_android.data.remote.dto.PoiDto
import com.example.projet_android.data.repository.DefaultPoiRepository
import com.example.projet_android.domain.model.Poi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiRepositoryFallbackTest {
    @Test
    fun `uses local fallback when remote fails`() = runBlocking {
        val remoteService = object : PoiApiService {
            override suspend fun getPois(): List<PoiDto> {
                error("Network error")
            }
        }
        val localDataSource = object : PoiLocalDataSource {
            override fun loadPois(): List<Poi> {
                return listOf(
                    Poi(
                        id = "local_1",
                        name = "Local Poi",
                        latitude = 48.0,
                        longitude = 2.0,
                        description = "local",
                        referenceImageAssetPath = "reference_images/local.png",
                        audioResName = "guide_local",
                        orbMatchThreshold = 10
                    )
                )
            }
        }

        val repository = DefaultPoiRepository(remoteService, localDataSource)
        val result = repository.getPois()

        assertTrue(result.isSuccess)
        assertEquals("local_1", result.getOrNull()?.firstOrNull()?.id)
    }
}
