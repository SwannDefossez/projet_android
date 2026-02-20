package com.example.projet_android.domain.usecase

import com.example.projet_android.data.repository.PoiRepository
import com.example.projet_android.domain.model.Poi

class LoadPoisUseCase(private val poiRepository: PoiRepository) {
    suspend operator fun invoke(): Result<List<Poi>> = poiRepository.getPois()
}
