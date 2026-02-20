package com.example.projet_android

import com.example.projet_android.data.remote.dto.OsrmGeometryDto
import com.example.projet_android.data.remote.dto.OsrmRouteDto
import com.example.projet_android.data.remote.dto.OsrmRouteResponse
import com.example.projet_android.data.repository.OsrmRouteMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class OsrmRouteMapperTest {
    @Test
    fun `maps coordinates from lon-lat to lat-lon`() {
        val response = OsrmRouteResponse(
            routes = listOf(
                OsrmRouteDto(
                    distance = 1200.0,
                    duration = 540.0,
                    geometry = OsrmGeometryDto(
                        coordinates = listOf(
                            listOf(2.294481, 48.858370),
                            listOf(2.337644, 48.860611)
                        )
                    )
                )
            )
        )

        val mapped = OsrmRouteMapper.toDomain(response)

        assertEquals(1200.0, mapped.distanceMeters, 0.001)
        assertEquals(540.0, mapped.durationSeconds, 0.001)
        assertEquals(48.858370, mapped.polylinePoints.first().latitude, 0.000001)
        assertEquals(2.294481, mapped.polylinePoints.first().longitude, 0.000001)
    }
}
