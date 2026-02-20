package com.example.projet_android.di

import android.content.Context
import com.example.projet_android.data.local.AssetPoiLocalDataSource
import com.example.projet_android.data.repository.DefaultPoiRepository
import com.example.projet_android.data.repository.DefaultRoutingRepository
import com.example.projet_android.data.repository.OpenCvRecognitionRepository
import com.example.projet_android.data.repository.PoiRepository
import com.example.projet_android.data.repository.RecognitionRepository
import com.example.projet_android.data.repository.RoutingRepository
import com.example.projet_android.data.remote.NetworkModule
import com.example.projet_android.domain.usecase.EvaluateRecognitionUseCase
import com.google.gson.Gson

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val poiApiService = NetworkModule.createPoiApiService(appContext)
    private val routingApiService = NetworkModule.createRoutingApiService()
    private val poiLocalDataSource = AssetPoiLocalDataSource(appContext, gson)

    val poiRepository: PoiRepository by lazy {
        DefaultPoiRepository(
            poiApiService = poiApiService,
            localDataSource = poiLocalDataSource
        )
    }

    val routingRepository: RoutingRepository by lazy {
        DefaultRoutingRepository(routingApiService = routingApiService)
    }

    val recognitionRepository: RecognitionRepository by lazy {
        OpenCvRecognitionRepository(
            context = appContext,
            evaluateRecognitionUseCase = EvaluateRecognitionUseCase()
        )
    }
}
