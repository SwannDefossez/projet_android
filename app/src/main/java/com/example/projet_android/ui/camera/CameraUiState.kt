package com.example.projet_android.ui.camera

import com.example.projet_android.domain.model.RecognitionResult

data class CameraUiState(
    val isAnalyzing: Boolean = false,
    val result: RecognitionResult? = null,
    val errorMessage: String? = null
)
