package com.darim.ui.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.darim.domain.model.Location

object UserLocationManager {

    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    private var lastKnownLocation: Location? = null

    /**
     * Обновляет местоположение пользователя
     */
    fun updateLocation(location: Location) {
        lastKnownLocation = location
        _userLocation.postValue(location)
    }

    /**
     * Получает последнее известное местоположение
     */
    fun getLastKnownLocation(): Location? {
        return lastKnownLocation
    }

    /**
     * Получает последнее известное местоположение из Android LocationManager
     */
    fun getLastKnownLocationFromDevice(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = locationManager.getProviders(true)
        var bestLocation: android.location.Location? = null

        for (provider in providers) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }

            if (location != null && (bestLocation == null ||
                        location.accuracy < bestLocation.accuracy)) {
                bestLocation = location
            }
        }

        return bestLocation?.let {
            Location(
                lat = it.latitude,
                lng = it.longitude,
                address = getAddressFromLocation(context, it.latitude, it.longitude)
            )
        }
    }

    /**
     * Проверяет наличие разрешения на геолокацию
     */
    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Получает адрес из координат (заглушка)
     */
    private fun getAddressFromLocation(context: Context, lat: Double, lng: Double): String {
        // TODO: Реализовать через Geocoder
        return "Москва, Россия"
    }

    /**
     * Очищает местоположение (при выходе из системы)
     */
    fun clearLocation() {
        lastKnownLocation = null
        _userLocation.postValue(null)
    }
}