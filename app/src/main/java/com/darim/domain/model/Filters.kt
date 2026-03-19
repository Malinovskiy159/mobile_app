package com.darim.domain.model

data class Filters(
    val category: String? = null,
    val status: String? = null,
    val ownerId: String? = null,
    val excludeOwnerId: String? = null,
    val query: String? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val sortBy: SortType = SortType.NEWEST
)
