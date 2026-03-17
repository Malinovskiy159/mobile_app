// domain/usecase/item/PublishItemUseCase.kt
package com.darim.domain.usecase.item

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.UserRepository
import com.darim.ui.utils.PhotoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PublishItemUseCase(
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository,
    private val context: Context
) {

    data class PublishRequest(
        val title: String,
        val category: String,
        val description: String,
        val location: Location,
        val ownerId: String,
        val ownerName: String?,
        val ownerPhone: String?,
        val photoUris: List<Uri>,  // Временные URI фото (из галереи/камеры)
        val photoFiles: List<java.io.File>? = null // Временные файлы из камеры
    )

    sealed class PublishResult {
        data class Success(val itemId: String, val item: Item) : PublishResult()
        data class Error(val message: String, val code: ErrorCode) : PublishResult()
        object Loading : PublishResult()

        enum class ErrorCode {
            EMPTY_TITLE,
            EMPTY_CATEGORY,
            EMPTY_DESCRIPTION,
            INVALID_LOCATION,
            NO_PHOTOS,
            USER_NOT_FOUND,
            SAVE_PHOTOS_FAILED,
            REPOSITORY_ERROR,
            UNKNOWN_ERROR
        }
    }

    fun execute(request: PublishRequest): LiveData<PublishResult> {
        return liveData(Dispatchers.IO) {
            emit(PublishResult.Loading)

            try {
                // 1. Валидация данных
                val validationError = validateRequest(request)
                if (validationError != null) {
                    emit(validationError)
                    return@liveData
                }

                // 2. Проверка существования пользователя
                val user = userRepository.getUser(request.ownerId)
                if (user == null) {
                    emit(PublishResult.Error(
                        "Пользователь не найден",
                        PublishResult.ErrorCode.USER_NOT_FOUND
                    ))
                    return@liveData
                }

                // 3. Генерация ID для новой вещи
                val itemId = UUID.randomUUID().toString()

                // 4. Сохранение фото во внутреннее хранилище
                val savedPhotoPaths = savePhotos(request, itemId)
                if (savedPhotoPaths.isEmpty()) {
                    emit(PublishResult.Error(
                        "Не удалось сохранить фотографии",
                        PublishResult.ErrorCode.SAVE_PHOTOS_FAILED
                    ))
                    return@liveData
                }

                // 5. Создание объекта Item
                val newItem = Item(
                    id = itemId,
                    title = request.title.trim(),
                    category = request.category,
                    description = request.description.trim(),
                    photos = savedPhotoPaths,
                    location = request.location,
                    ownerId = request.ownerId,
                    ownerName = request.ownerName ?: user.name,
                    ownerPhone = request.ownerPhone ?: user.phone,
                    status = ItemStatus.AVAILABLE,
                    createdAt = System.currentTimeMillis(),
                    bookedBy = null,
                    views = 0
                )

                // 6. Сохранение в репозиторий (JSON)
                val result = itemRepository.publishItem(newItem)

                if (result.isSuccess) {
                    emit(PublishResult.Success(itemId, newItem))
                } else {
                    // Если не удалось сохранить в JSON, удаляем сохраненные фото
                    deleteSavedPhotos(savedPhotoPaths)

                    emit(PublishResult.Error(
                        result.exceptionOrNull()?.message ?: "Ошибка сохранения объявления",
                        PublishResult.ErrorCode.REPOSITORY_ERROR
                    ))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                emit(PublishResult.Error(
                    e.message ?: "Неизвестная ошибка",
                    PublishResult.ErrorCode.UNKNOWN_ERROR
                ))
            }
        }
    }

    private fun deleteSavedPhotos(photoPaths: List<String>) {
        photoPaths.forEach { path ->
            PhotoManager.deletePhoto(context, path)
        }
    }

    private fun validateRequest(request: PublishRequest): PublishResult.Error? {
        return when {
            request.title.isBlank() -> PublishResult.Error(
                "Введите название вещи",
                PublishResult.ErrorCode.EMPTY_TITLE
            )
            request.category.isBlank() -> PublishResult.Error(
                "Выберите категорию",
                PublishResult.ErrorCode.EMPTY_CATEGORY
            )
            request.description.isBlank() -> PublishResult.Error(
                "Введите описание",
                PublishResult.ErrorCode.EMPTY_DESCRIPTION
            )
            request.location.lat == 0.0 && request.location.lng == 0.0 -> PublishResult.Error(
                "Укажите местоположение",
                PublishResult.ErrorCode.INVALID_LOCATION
            )
            request.photoUris.isEmpty() && (request.photoFiles == null || request.photoFiles.isEmpty()) ->
                PublishResult.Error(
                    "Добавьте хотя бы одно фото",
                    PublishResult.ErrorCode.NO_PHOTOS
                )
            else -> null
        }
    }

    private suspend fun savePhotos(request: PublishRequest, itemId: String): List<String> {
        return withContext(Dispatchers.IO) {
            val savedPaths = mutableListOf<String>()

            try {
                // Сохраняем фото из галереи (Uri)
                request.photoUris.forEach { uri ->
                    val savedPath = PhotoManager.savePhotoToInternalStorage(
                        context,
                        uri,
                        itemId
                    )
                    if (savedPath != null) {
                        savedPaths.add(savedPath)
                    }
                }

                // Сохраняем фото из камеры (File)
                request.photoFiles?.forEach { file ->
                    if (file.exists()) {
                        val savedPath = PhotoManager.saveCameraPhotoToInternalStorage(
                            context,
                            file,
                            itemId
                        )
                        if (savedPath != null) {
                            savedPaths.add(savedPath)
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            savedPaths
        }
    }

    private fun deleteSavedPaths(photoPaths: List<String>) {
        photoPaths.forEach { path ->
            PhotoManager.deletePhoto(context, path)
        }
    }

    // Вспомогательные методы для создания запроса

    fun createRequest(
        title: String,
        category: String,
        description: String,
        location: Location,
        ownerId: String,
        ownerName: String?,
        ownerPhone: String?,
        photoUris: List<Uri>
    ): PublishRequest {
        return PublishRequest(
            title = title,
            category = category,
            description = description,
            location = location,
            ownerId = ownerId,
            ownerName = ownerName,
            ownerPhone = ownerPhone,
            photoUris = photoUris,
            photoFiles = null
        )
    }

    fun createRequestWithCameraFiles(
        title: String,
        category: String,
        description: String,
        location: Location,
        ownerId: String,
        ownerName: String?,
        ownerPhone: String?,
        photoUris: List<Uri>,
        photoFiles: List<java.io.File>
    ): PublishRequest {
        return PublishRequest(
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
    }
}