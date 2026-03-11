package com.darim.domain.model

import java.io.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Location(
    val lat: Double,
    val lng: Double,
    val address: String
) : Serializable {  // ← Добавляем Serializable
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(other.lat - this.lat)
        val dLng = Math.toRadians(other.lng - this.lng)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(this.lat)) * cos(Math.toRadians(other.lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}