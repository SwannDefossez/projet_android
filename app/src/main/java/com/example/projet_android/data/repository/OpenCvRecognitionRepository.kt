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
                var capturedImage: Mat? = null
                var referenceBuffer: MatOfByte? = null
                var referenceImage: Mat? = null
                var orb: ORB? = null
                var keyPointsCaptured: MatOfKeyPoint? = null
                var keyPointsReference: MatOfKeyPoint? = null
                var descriptorCaptured: Mat? = null
                var descriptorReference: Mat? = null
                var maskCaptured: Mat? = null
                var maskReference: Mat? = null
                var matcher: BFMatcher? = null
                var matches: MatOfDMatch? = null

                try {
                    capturedImage = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_GRAYSCALE)
                    require(!capturedImage.empty()) { "Captured image is empty" }

                    val referenceBytes = context.assets.open(poi.referenceImageAssetPath).use { it.readBytes() }
                    referenceBuffer = MatOfByte(*referenceBytes)
                    referenceImage = Imgcodecs.imdecode(referenceBuffer, Imgcodecs.IMREAD_GRAYSCALE)
                    require(!referenceImage.empty()) { "Reference image is empty" }

                    orb = ORB.create()
                    keyPointsCaptured = MatOfKeyPoint()
                    keyPointsReference = MatOfKeyPoint()
                    descriptorCaptured = Mat()
                    descriptorReference = Mat()
                    maskCaptured = Mat()
                    maskReference = Mat()

                    orb.detectAndCompute(
                        capturedImage,
                        maskCaptured,
                        keyPointsCaptured,
                        descriptorCaptured
                    )
                    orb.detectAndCompute(
                        referenceImage,
                        maskReference,
                        keyPointsReference,
                        descriptorReference
                    )

                    if (descriptorCaptured.empty() || descriptorReference.empty()) {
                        return@runCatching RecognitionResult(
                            poiId = poi.id,
                            goodMatches = 0,
                            isMatch = false,
                            confidence = 0f
                        )
                    }

                    matcher = BFMatcher.create(Core.NORM_HAMMING, true)
                    matches = MatOfDMatch()
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
                } finally {
                    matches?.release()
                    matcher?.clear()
                    maskReference?.release()
                    maskCaptured?.release()
                    descriptorReference?.release()
                    descriptorCaptured?.release()
                    keyPointsReference?.release()
                    keyPointsCaptured?.release()
                    orb?.clear()
                    referenceImage?.release()
                    referenceBuffer?.release()
                    capturedImage?.release()
                }
            }
        }
    }
}
