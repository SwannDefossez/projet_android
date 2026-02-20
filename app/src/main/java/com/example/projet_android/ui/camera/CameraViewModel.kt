package com.example.projet_android.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.usecase.MatchPoiUseCase
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(
    private val matchPoiUseCase: MatchPoiUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    fun analyzePhoto(poi: Poi, imagePath: String) {
        if (_uiState.value.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isAnalyzing = true, result = null, errorMessage = null)
            }

            val result = try {
                matchPoiUseCase(poi, imagePath)
            } finally {
                File(imagePath).delete()
            }

            result.onSuccess { recognition ->
                _uiState.update {
                    it.copy(isAnalyzing = false, result = recognition, errorMessage = null)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        result = null,
                        errorMessage = throwable.message ?: "Image analysis failed."
                    )
                }
            }
        }
    }

    fun clearTransientState() {
        _uiState.update { it.copy(result = null, errorMessage = null) }
    }
}

class CameraViewModelFactory(
    private val matchPoiUseCase: MatchPoiUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            return CameraViewModel(matchPoiUseCase = matchPoiUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
