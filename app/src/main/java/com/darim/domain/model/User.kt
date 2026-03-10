package com.darim.domain.model

data class User(
    val id: String,
    val name: String,
    val phone: String,
    val rating: Float,
    val reviews: List<Review>,
    val itemsGiven: Int,
    val itemsTaken: Int
) {
    fun getAverageRating(): Float {
        if (reviews.isEmpty()) return rating
        // Recalculate Avarage Rating
        return reviews.map { it.rating }.average().toFloat()
    }
}