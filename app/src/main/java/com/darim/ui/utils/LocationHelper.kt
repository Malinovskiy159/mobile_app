// ui/utils/LocationHelper.kt
package com.darim.ui.utils

import kotlin.math.*

object LocationHelper {

    /**
     * Рассчитывает расстояние между двумя точками на Земле (формула гаверсинусов)
     * @return расстояние в километрах
     */
    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371 // Радиус Земли в км

        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)

        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * Форматирует расстояние в удобочитаемый вид
     */
    fun formatDistance(distanceKm: Double): String {
        return when {
            distanceKm < 1.0 -> "${(distanceKm * 1000).toInt()} м"
            distanceKm < 10.0 -> "${String.format("%.1f", distanceKm)} км"
            else -> "${distanceKm.toInt()} км"
        }
    }

    /**
     * Проверяет, находится ли точка в радиусе от центра
     */
    fun isWithinRadius(
        centerLat: Double,
        centerLng: Double,
        pointLat: Double,
        pointLng: Double,
        radiusKm: Double
    ): Boolean {
        return calculateDistance(centerLat, centerLng, pointLat, pointLng) <= radiusKm
    }
}