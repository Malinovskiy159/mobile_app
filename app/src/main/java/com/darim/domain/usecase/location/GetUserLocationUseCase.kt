package com.darim.domain.usecase.location

import com.darim.domain.model.Location
import com.darim.domain.repository.LocationRepository

class GetUserLocationUseCase(private val locationRepository: LocationRepository) {
    suspend fun execute(): Result<Location> {
        if (!checkPermissions()) {
            return Result.failure(SecurityException("Геолокация отключена или нет прав"))
        }

        val location = locationRepository.getCurrentLocation()
        return if (location != null) {
            Result.success(location)
        } else {
            Result.failure(Exception("Не удалось определить местоположение"))
        }
    }

    private fun checkPermissions(): Boolean {
        return locationRepository.isLocationEnabled()
    }
}