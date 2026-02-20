package com.example.projet_android.data.repository

import android.content.Context
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RecognitionResult
import com.example.projet_android.domain.usecase.EvaluateRecognitionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs

class OpenCvRecognitionRepository(
    private val context: Context,
    private val evaluateRecognitionUseCase: EvaluateRecognitionUseCase
) : RecognitionRepository {

    init {
        if (!OpenCVLoader.initDebug()) {
            throw IllegalStateException("OpenCV initialization failed")
        }
    }

    override suspend fun matchCapturedImage(poi: Poi, imagePath: String): Result<RecognitionResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val capturedImage = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_GRAYSCALE)
                require(!capturedImage.empty()) { "Captured image is empty" }

                val referenceBytes = context.assets.open(poi.referenceImageAssetPath).use { it.readBytes() }
                val referenceImage = Imgcodecs.imdecode(
                    MatOfByte(*referenceBytes),
                    Imgcodecs.IMREAD_GRAYSCALE
                )
                require(!referenceImage.empty()) { "Reference image is empty" }

                val orb = ORB.create()
                val keyPointsCaptured = MatOfKeyPoint()
                val keyPointsReference = MatOfKeyPoint()
                val descriptorCaptured = Mat()
                val descriptorReference = Mat()

                orb.detectAndCompute(capturedImage, Mat(), keyPointsCaptured, descriptorCaptured)
                orb.detectAndCompute(referenceImage, Mat(), keyPointsReference, descriptorReference)

                if (descriptorCaptured.empty() || descriptorReference.empty()) {
                    return@runCatching RecognitionResult(
                        poiId = poi.id,
                        goodMatches = 0,
                        isMatch = false,
                        confidence = 0f
                    )
                }

                val matcher = BFMatcher.create(Core.NORM_HAMMING, true)
                val matches = MatOfDMatch()
                matcher.match(descriptorCaptured, descriptorReference, matches)

                val goodMatches = matches.toArray().count { it.distance < 50f }
                val isMatch = evaluateRecognitionUseCase.isMatch(goodMatches, poi.orbMatchThreshold)
                val confidence = evaluateRecognitionUseCase.confidence(goodMatches, poi.orbMatchThreshold)

                RecognitionResult(
                    poiId = poi.id,
                    goodMatches = goodMatches,
                    isMatch = isMatch,
                    confidence = confidence
                )
            }
        }
    }
}
