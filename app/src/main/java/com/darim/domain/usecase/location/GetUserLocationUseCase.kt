// domain/usecase/location/GetUserLocationUseCase.kt
package com.darim.domain.usecase.location

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Location
import com.darim.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers

class GetUserLocationUseCase(
    private val locationRepository: LocationRepository
) {

    sealed class LocationResult {
        data class Success(val location: Location) : LocationResult()
        data class Error(val message: String) : LocationResult()
        object LocationDisabled : LocationResult()
        object PermissionDenied : LocationResult()
    }

    fun execute(): LiveData<LocationResult> {
        return liveData(Dispatchers.IO) {
            try {
                // Проверяем, включена ли геолокация
                if (!locationRepository.isLocationEnabled()) {
                    emit(LocationResult.LocationDisabled)
                    return@liveData
                }

                // Получаем текущую локацию
                val location = locationRepository.getCurrentLocation()

                if (location != null) {
                    emit(LocationResult.Success(location))
                } else {
                    // Пробуем запросить обновление
                    val newLocation = locationRepository.requestLocationUpdate()
                    if (newLocation != null) {
                        emit(LocationResult.Success(newLocation))
                    } else {
                        emit(LocationResult.Error("Не удалось получить местоположение"))
                    }
                }
            } catch (e: SecurityException) {
                emit(LocationResult.PermissionDenied)
            } catch (e: Exception) {
                emit(LocationResult.Error(e.message ?: "Ошибка получения местоположения"))
            }
        }
    }

    fun checkLocationEnabled(): Boolean {
        return locationRepository.isLocationEnabled()
    }
}