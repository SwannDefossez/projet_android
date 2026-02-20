package com.example.projet_android.data.repository

import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RecognitionResult

interface RecognitionRepository {
    suspend fun matchCapturedImage(poi: Poi, imagePath: String): Result<RecognitionResult>
}
