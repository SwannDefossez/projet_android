package com.example.projet_android.data.repository

import com.example.projet_android.data.local.PoiLocalDataSource
import com.example.projet_android.data.remote.PoiApiService
import com.example.projet_android.domain.model.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultPoiRepository(
    private val poiApiService: PoiApiService,
    private val localDataSource: PoiLocalDataSource
) : PoiRepository {

    override suspend fun getPois(): Result<List<Poi>> = withContext(Dispatchers.IO) {
        val remoteResult = runCatching {
            poiApiService.getPois().map { it.toDomain() }
        }

        if (remoteResult.isSuccess && remoteResult.getOrThrow().isNotEmpty()) {
            return@withContext remoteResult
        }

        val localResult = runCatching { localDataSource.loadPois() }
        if (localResult.isSuccess) {
            return@withContext localResult
        }

        val failure = localResult.exceptionOrNull()
            ?: remoteResult.exceptionOrNull()
            ?: IllegalStateException(
                "Impossible de charger les lieux d\u2019int\u00E9r\u00EAt depuis les sources distante et locale."
            )

        Result.failure(failure)
    }
}


