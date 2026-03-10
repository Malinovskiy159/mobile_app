package com.darim.domain.model

data class Review(
    val fromUserId: String,
    val toUserId: String,
    val rating: Int,
    val comment: String,
    val date: Long
)