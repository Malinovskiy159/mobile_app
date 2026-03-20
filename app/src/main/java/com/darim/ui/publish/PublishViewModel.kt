package com.darim.ui.publish

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darim.domain.model.Location
import com.darim.domain.usecase.item.PublishItemUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PublishViewModel(
    private val publishItemUseCase: PublishItemUseCase  // ← обязательный параметр
) : ViewModel() {

    sealed class PublishUiState {
        object Idle : PublishUiState()
        object Loading : PublishUiState()
        object Success : PublishUiState()
        data class Error(val message: String) : PublishUiState()
    }

    private val _publishResult = MutableStateFlow<PublishUiState>(PublishUiState.Idle)
    val publishResult: StateFlow<PublishUiState> = _publishResult.asStateFlow()

    fun publishItem(
        title: String,
        category: String,
        description: String,
        location: Location,
        ownerId: String,
        ownerName: String?,
        ownerPhone: String?,
        photoUris: List<Uri>,
        photoFiles: List<File>
    ) {
        viewModelScope.launch {
            _publishResult.value = PublishUiState.Loading

            val request = if (photoFiles.isNotEmpty()) {
                publishItemUseCase.createRequestWithCameraFiles(
                    title = title,
                    category = category,
                    description = description,
                    location = location,
                    ownerId = ownerId,
                    ownerName = ownerName,
                    ownerPhone = ownerPhone,
                    photoUris = photoUris,
                    photoFiles = photoFiles
                )
            } else {
                publishItemUseCase.createRequest(
                    title = title,
                    category = category,
                    description = description,
                    location = location,
                    ownerId = ownerId,
                    ownerName = ownerName,
                    ownerPhone = ownerPhone,
                    photoUris = photoUris
                )
            }

            publishItemUseCase.execute(request).observeForever { result ->
                when (result) {
                    is PublishItemUseCase.PublishResult.Success -> {
                        _publishResult.value = PublishUiState.Success
                    }
                    is PublishItemUseCase.PublishResult.Error -> {
                        _publishResult.value = PublishUiState.Error(result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun resetState() {
        _publishResult.value = PublishUiState.Idle
    }
}