package com.example.projet_android.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projet_android.R
import com.example.projet_android.TourGuideApplication
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.model.RecognitionResult
import com.example.projet_android.domain.usecase.MatchPoiUseCase
import com.example.projet_android.ui.guide.GuideActivity
import com.example.projet_android.ui.navigation.NavigationExtras
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CameraActivity : FragmentActivity() {

    private val viewModel: CameraViewModel by viewModels {
        val appContainer = (application as TourGuideApplication).appContainer
        CameraViewModelFactory(MatchPoiUseCase(appContainer.recognitionRepository))
    }

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var statusText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var progressBar: ProgressBar

    private var imageCapture: ImageCapture? = null
    private var currentPoi: Poi? = null
    private var hasNavigatedToGuide = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                statusText.text = getString(R.string.camera_permission_denied)
                captureButton.isEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        statusText = findViewById(R.id.cameraStatusText)
        feedbackText = findViewById(R.id.cameraFeedbackText)
        progressBar = findViewById(R.id.cameraProgressBar)

        currentPoi = readPoiFromIntent()
        if (currentPoi == null) {
            Toast.makeText(this, R.string.camera_missing_poi, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        statusText.text = getString(R.string.camera_prompt, currentPoi!!.name)
        feedbackText.text = ""
        captureButton.setOnClickListener {
            captureAndAnalyze()
        }

        observeUiState()
        requestCameraPermissionIfNeeded()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    captureButton.isEnabled = !state.isAnalyzing
                    progressBar.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
                    if (state.isAnalyzing) {
                        statusText.text = getString(R.string.camera_analyzing)
                        feedbackText.text = ""
                    }

                    state.errorMessage?.let { error ->
                        Log.e(TAG, "Photo analysis error: $error")
                        statusText.text = getString(R.string.camera_analysis_error)
                        Toast.makeText(
                            this@CameraActivity,
                            R.string.camera_analysis_error,
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.clearTransientState()
                    }

                    val result = state.result ?: return@collect
                    val threshold = currentPoi?.orbMatchThreshold ?: 1
                    feedbackText.text = buildFeedbackText(result, threshold)
                    if (result.isMatch) {
                        if (!hasNavigatedToGuide) {
                            hasNavigatedToGuide = true
                            Toast.makeText(
                                this@CameraActivity,
                                R.string.camera_match_success,
                                Toast.LENGTH_SHORT
                            ).show()
                            openGuide(result)
                        }
                    } else {
                        statusText.text = getString(R.string.camera_not_match)
                        Toast.makeText(this@CameraActivity, R.string.camera_try_again, Toast.LENGTH_SHORT)
                            .show()
                    }
                    viewModel.clearTransientState()
                }
            }
        }
    }

    private fun readPoiFromIntent(): Poi? {
        val id = intent.getStringExtra(NavigationExtras.EXTRA_POI_ID) ?: return null
        val name = intent.getStringExtra(NavigationExtras.EXTRA_POI_NAME) ?: return null
        val address = intent.getStringExtra(NavigationExtras.EXTRA_POI_ADDRESS) ?: ""
        val latitude = intent.getDoubleExtra(NavigationExtras.EXTRA_POI_LAT, Double.NaN)
        val longitude = intent.getDoubleExtra(NavigationExtras.EXTRA_POI_LON, Double.NaN)
        val description = intent.getStringExtra(NavigationExtras.EXTRA_POI_DESC) ?: ""
        val referenceImage = intent.getStringExtra(NavigationExtras.EXTRA_POI_REF_IMAGE) ?: return null
        val audioResName = intent.getStringExtra(NavigationExtras.EXTRA_POI_AUDIO) ?: return null
        val threshold = intent.getIntExtra(NavigationExtras.EXTRA_POI_THRESHOLD, 20)

        if (latitude.isNaN() || longitude.isNaN()) return null

        return Poi(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            description = description,
            referenceImageAssetPath = referenceImage,
            audioResName = audioResName,
            orbMatchThreshold = threshold
        )
    }

    private fun requestCameraPermissionIfNeeded() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            statusText.text = getString(R.string.camera_ready)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        val poi = currentPoi ?: return
        val captureUseCase = imageCapture ?: run {
            statusText.text = getString(R.string.camera_not_ready)
            return
        }

        val photoFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModel.analyzePhoto(poi, photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Camera capture failed", exception)
                    statusText.text = getString(R.string.camera_capture_error)
                }
            }
        )
    }

    private fun buildFeedbackText(result: RecognitionResult, threshold: Int): String {
        val summary = getString(
            R.string.camera_feedback_summary,
            result.goodMatches,
            CameraFeedbackAdvisor.confidencePercent(result.confidence)
        )
        val adviceRes = when (CameraFeedbackAdvisor.adviceFor(result, threshold)) {
            CameraAdvice.MATCH_CONFIRMED -> R.string.camera_tip_match_ok
            CameraAdvice.MOVE_CLOSER -> R.string.camera_tip_move_closer
            CameraAdvice.REFRAME -> R.string.camera_tip_reframe
            CameraAdvice.IMPROVE_LIGHTING -> R.string.camera_tip_light
        }
        return "$summary\n${getString(adviceRes)}"
    }

    private fun openGuide(result: RecognitionResult) {
        val poi = currentPoi ?: return
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(NavigationExtras.EXTRA_POI_ID, poi.id)
                putExtra(NavigationExtras.EXTRA_RECOGNITION_GOOD_MATCHES, result.goodMatches)
                putExtra(NavigationExtras.EXTRA_RECOGNITION_CONFIDENCE, result.confidence)
            }
        )
        val intent = Intent(this, GuideActivity::class.java).apply {
            putExtra(NavigationExtras.EXTRA_POI_ID, poi.id)
            putExtra(NavigationExtras.EXTRA_POI_NAME, poi.name)
            putExtra(NavigationExtras.EXTRA_POI_ADDRESS, poi.address)
            putExtra(NavigationExtras.EXTRA_POI_DESC, poi.description)
            putExtra(NavigationExtras.EXTRA_POI_AUDIO, poi.audioResName)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}
