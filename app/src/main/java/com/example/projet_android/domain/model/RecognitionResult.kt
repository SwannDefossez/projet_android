package com.example.projet_android.domain.model

data class RecognitionResult(
    val poiId: String,
    val goodMatches: Int,
    val isMatch: Boolean,
    val confidence: Float
)
