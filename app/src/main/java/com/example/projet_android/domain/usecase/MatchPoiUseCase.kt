package com.example.projet_android.domain.usecase

import com.example.projet_android.data.repository.RecognitionRepository
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RecognitionResult

class MatchPoiUseCase(private val recognitionRepository: RecognitionRepository) {
    suspend operator fun invoke(poi: Poi, imagePath: String): Result<RecognitionResult> {
        return recognitionRepository.matchCapturedImage(poi, imagePath)
    }
}
