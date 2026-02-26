package com.example.projet_android

import com.example.projet_android.domain.model.RecognitionResult
import com.example.projet_android.ui.camera.CameraAdvice
import com.example.projet_android.ui.camera.CameraFeedbackAdvisor
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFeedbackAdvisorTest {
    @Test
    fun `returns match advice when recognition is successful`() {
        val result = RecognitionResult(
            poiId = "poi",
            goodMatches = 24,
            isMatch = true,
            confidence = 0.82f
        )

        val advice = CameraFeedbackAdvisor.adviceFor(result, threshold = 18)

        assertEquals(CameraAdvice.MATCH_CONFIRMED, advice)
        assertEquals(82, CameraFeedbackAdvisor.confidencePercent(result.confidence))
    }

    @Test
    fun `returns improvement advice for weak recognition`() {
        val result = RecognitionResult(
            poiId = "poi",
            goodMatches = 2,
            isMatch = false,
            confidence = 0.12f
        )

        val advice = CameraFeedbackAdvisor.adviceFor(result, threshold = 18)

        assertEquals(CameraAdvice.IMPROVE_LIGHTING, advice)
    }
}
