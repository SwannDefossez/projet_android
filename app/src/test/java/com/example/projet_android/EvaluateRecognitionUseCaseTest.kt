package com.example.projet_android

import com.example.projet_android.domain.usecase.EvaluateRecognitionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateRecognitionUseCaseTest {
    private val useCase = EvaluateRecognitionUseCase()

    @Test
    fun `isMatch returns true when threshold is reached`() {
        assertTrue(useCase.isMatch(goodMatches = 20, threshold = 20))
        assertFalse(useCase.isMatch(goodMatches = 19, threshold = 20))
    }

    @Test
    fun `confidence is clamped between 0 and 1`() {
        assertEquals(0f, useCase.confidence(goodMatches = 0, threshold = 20))
        assertEquals(0.5f, useCase.confidence(goodMatches = 10, threshold = 20))
        assertEquals(1f, useCase.confidence(goodMatches = 30, threshold = 20))
    }
}
