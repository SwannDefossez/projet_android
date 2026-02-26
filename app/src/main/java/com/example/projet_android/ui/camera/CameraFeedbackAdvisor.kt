package com.example.projet_android.ui.camera

import com.example.projet_android.domain.model.RecognitionResult

enum class CameraAdvice {
    MATCH_CONFIRMED,
    MOVE_CLOSER,
    REFRAME,
    IMPROVE_LIGHTING
}

object CameraFeedbackAdvisor {
    fun adviceFor(result: RecognitionResult, threshold: Int): CameraAdvice {
        if (result.isMatch) return CameraAdvice.MATCH_CONFIRMED
        if (result.goodMatches <= 2) return CameraAdvice.IMPROVE_LIGHTING

        val safeThreshold = threshold.coerceAtLeast(1)
        val ratio = result.goodMatches.toFloat() / safeThreshold.toFloat()
        return if (ratio < 0.4f) {
            CameraAdvice.MOVE_CLOSER
        } else {
            CameraAdvice.REFRAME
        }
    }

    fun confidencePercent(confidence: Float): Int {
        return (confidence.coerceIn(0f, 1f) * 100f).toInt()
    }
}
