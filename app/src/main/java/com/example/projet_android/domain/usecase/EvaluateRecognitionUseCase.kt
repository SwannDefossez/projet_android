package com.example.projet_android.domain.usecase

class EvaluateRecognitionUseCase {
    fun isMatch(goodMatches: Int, threshold: Int): Boolean {
        return goodMatches >= threshold
    }

    fun confidence(goodMatches: Int, threshold: Int): Float {
        if (threshold <= 0) return 1f
        return (goodMatches.toFloat() / threshold.toFloat()).coerceIn(0f, 1f)
    }
}
