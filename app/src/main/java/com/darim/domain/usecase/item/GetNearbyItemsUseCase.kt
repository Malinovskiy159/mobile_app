// domain/usecase/item/GetNearbyItemsUseCase.kt
package com.darim.domain.usecase.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.domain.repository.ItemRepository
import com.darim.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.*

class GetNearbyItemsUseCase(
    private val itemRepository: ItemRepository,
    private val locationRepository: LocationRepository
) {

    data class NearbyItem(
        val item: Item,
        val distanceKm: Double,
        val distanceText: String,
        val estimatedTravelTime: Int // в минутах
    )

    sealed class NearbyItemsResult {
        data class Success(val items: List<NearbyItem>) : NearbyItemsResult()
        data class Error(val message: String) : NearbyItemsResult()
        object Loading : NearbyItemsResult()
        object Empty : NearbyItemsResult()
        object LocationUnavailable : NearbyItemsResult()
        object LocationDisabled : NearbyItemsResult()
    }

    enum class SortBy {
        DISTANCE,       // по расстоянию
        NEWEST,         // сначала новые
        POPULARITY,     // по популярности (по количеству просмотров/броней)
        AVAILABILITY    // по доступности
    }

    enum class TravelMode {
        WALKING,
        BICYCLING,
        DRIVING
    }

    data class SearchParams(
        val center: Location? = null,
        val radiusKm: Double = 5.0,
        val categories: List<String>? = null,
        val sortBy: SortBy = SortBy.DISTANCE,
        val maxResults: Int = 50,
        val excludeUserItems: Boolean = true,
        val userId: String? = null,
        val onlyAvailable: Boolean = true,
        val travelMode: TravelMode = TravelMode.WALKING
    )

    // Исправленная версия с LiveData
    fun execute(params: SearchParams = SearchParams()): LiveData<NearbyItemsResult> {
        return liveData(Dispatchers.IO) {
            emit(NearbyItemsResult.Loading)

            try {
                // Получаем текущую локацию, если не указана
                val centerLocation = params.center ?: run {
                    if (!locationRepository.isLocationEnabled()) {
                        emit(NearbyItemsResult.LocationDisabled)
                        return@liveData
                    }

                    val location = locationRepository.getCurrentLocation()
                    if (location == null) {
                        emit(NearbyItemsResult.LocationUnavailable)
                        return@liveData
                    }
                    location
                }

                // Получаем вещи в радиусе
                val nearbyItems = itemRepository.getNearbyItems(
                    lat = centerLocation.lat,
                    lng = centerLocation.lng,
                    radius = params.radiusKm
                )

                if (nearbyItems.isEmpty()) {
                    emit(NearbyItemsResult.Empty)
                    return@liveData
                }

                // Преобразуем в NearbyItem с расчетом расстояния
                var resultItems = nearbyItems.map { item ->
                    val distance = calculateDistance(
                        centerLocation.lat, centerLocation.lng,
                        item.location.lat, item.location.lng
                    )

                    NearbyItem(
                        item = item,
                        distanceKm = distance,
                        distanceText = formatDistance(distance),
                        estimatedTravelTime = estimateTravelTime(distance, params.travelMode)
                    )
                }

                // Применяем фильтры
                resultItems = applyFilters(resultItems, params)

                // Сортируем
                resultItems = applySorting(resultItems, params.sortBy)

                // Ограничиваем количество
                if (resultItems.size > params.maxResults) {
                    resultItems = resultItems.take(params.maxResults)
                }

                if (resultItems.isEmpty()) {
                    emit(NearbyItemsResult.Empty)
                } else {
                    emit(NearbyItemsResult.Success(resultItems))
                }
            } catch (e: Exception) {
                emit(NearbyItemsResult.Error(e.message ?: "Ошибка поиска вещей рядом"))
            }
        }
    }

    // Альтернативная версия с Flow (более современный подход)
    fun executeFlow(params: SearchParams = SearchParams()): Flow<NearbyItemsResult> = flow {
        emit(NearbyItemsResult.Loading)

        try {
            val centerLocation = params.center ?: run {
                if (!locationRepository.isLocationEnabled()) {
                    emit(NearbyItemsResult.LocationDisabled)
                    return@flow
                }

                val location = locationRepository.getCurrentLocation()
                if (location == null) {
                    emit(NearbyItemsResult.LocationUnavailable)
                    return@flow
                }
                location
            }

            val nearbyItems = itemRepository.getNearbyItems(
                lat = centerLocation.lat,
                lng = centerLocation.lng,
                radius = params.radiusKm
            )

            if (nearbyItems.isEmpty()) {
                emit(NearbyItemsResult.Empty)
                return@flow
            }

            var resultItems = nearbyItems.map { item ->
                val distance = calculateDistance(
                    centerLocation.lat, centerLocation.lng,
                    item.location.lat, item.location.lng
                )

                NearbyItem(
                    item = item,
                    distanceKm = distance,
                    distanceText = formatDistance(distance),
                    estimatedTravelTime = estimateTravelTime(distance, params.travelMode)
                )
            }

            resultItems = applyFilters(resultItems, params)
            resultItems = applySorting(resultItems, params.sortBy)

            if (resultItems.size > params.maxResults) {
                resultItems = resultItems.take(params.maxResults)
            }

            if (resultItems.isEmpty()) {
                emit(NearbyItemsResult.Empty)
            } else {
                emit(NearbyItemsResult.Success(resultItems))
            }
        } catch (e: Exception) {
            emit(NearbyItemsResult.Error(e.message ?: "Ошибка поиска вещей рядом"))
        }
    }.flowOn(Dispatchers.IO)

    // Исправленный searchInRadius
    fun searchInRadius(
        lat: Double,
        lng: Double,
        radiusKm: Double
    ): LiveData<List<NearbyItem>> {
        return liveData(Dispatchers.IO) {
            try {
                val params = SearchParams(
                    center = Location(lat, lng, ""),
                    radiusKm = radiusKm
                )

                // Используем execute и обрабатываем результат напрямую
                val result = execute(params)
                // Здесь нужно подписаться на result, но это не правильно
                // Лучше сделать отдельную реализацию

                val items = itemRepository.getNearbyItems(lat, lng, radiusKm)
                val nearbyItems = items.map { item ->
                    val distance = calculateDistance(lat, lng, item.location.lat, item.location.lng)
                    NearbyItem(
                        item = item,
                        distanceKm = distance,
                        distanceText = formatDistance(distance),
                        estimatedTravelTime = estimateTravelTime(distance, TravelMode.WALKING)
                    )
                }.sortedBy { it.distanceKm }

                emit(nearbyItems)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    // Исправленный getItemsByCategory
    fun getItemsByCategory(
        category: String,
        lat: Double,
        lng: Double,
        radiusKm: Double = 5.0
    ): LiveData<List<NearbyItem>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getNearbyItems(lat, lng, radiusKm)
                    .filter { it.category == category && it.status == ItemStatus.AVAILABLE }

                val nearbyItems = items.map { item ->
                    val distance = calculateDistance(lat, lng, item.location.lat, item.location.lng)
                    NearbyItem(
                        item = item,
                        distanceKm = distance,
                        distanceText = formatDistance(distance),
                        estimatedTravelTime = estimateTravelTime(distance, TravelMode.WALKING)
                    )
                }.sortedBy { it.distanceKm }

                emit(nearbyItems)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    // Исправленный getHotspots
    fun getHotspots(
        lat: Double,
        lng: Double,
        radiusKm: Double
    ): LiveData<List<LocationHotspot>> {
        return liveData(Dispatchers.IO) {
            try {
                val items = itemRepository.getNearbyItems(lat, lng, radiusKm)

                // Группируем вещи по районам/кластерам
                val hotspots = items.groupBy { item ->
                    // Округляем координаты до сотых (примерно 1 км)
                    Pair(
                        (item.location.lat * 100).toInt() / 100.0,
                        (item.location.lng * 100).toInt() / 100.0
                    )
                }.map { (center, itemsInArea) ->
                    LocationHotspot(
                        centerLat = center.first,
                        centerLng = center.second,
                        itemCount = itemsInArea.size,
                        items = itemsInArea,
                        averageDistance = itemsInArea.map { item ->
                            calculateDistance(lat, lng, item.location.lat, item.location.lng)
                        }.average()
                    )
                }.sortedBy { it.averageDistance }

                emit(hotspots)
            } catch (e: Exception) {
                emit(emptyList())
            }
        }
    }

    // Остальные методы остаются без изменений
    private fun applyFilters(
        items: List<NearbyItem>,
        params: SearchParams
    ): List<NearbyItem> {
        return items.filter { nearbyItem ->
            var include = true

            if (params.categories != null && params.categories.isNotEmpty()) {
                include = include && params.categories.contains(nearbyItem.item.category)
            }

            if (params.onlyAvailable) {
                include = include && nearbyItem.item.status == ItemStatus.AVAILABLE
            }

            if (params.excludeUserItems && params.userId != null) {
                include = include && nearbyItem.item.ownerId != params.userId
            }

            include = include && nearbyItem.distanceKm <= params.radiusKm

            include
        }
    }

    private fun applySorting(
        items: List<NearbyItem>,
        sortBy: SortBy
    ): List<NearbyItem> {
        return when (sortBy) {
            SortBy.DISTANCE -> items.sortedBy { it.distanceKm }
            SortBy.NEWEST -> items.sortedByDescending { it.item.createdAt }
            SortBy.POPULARITY -> items.sortedByDescending { it.item.createdAt }
            SortBy.AVAILABILITY -> items.sortedBy { it.item.status }
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val R = 6371
        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun formatDistance(distanceKm: Double): String {
        return when {
            distanceKm < 1 -> "${(distanceKm * 1000).toInt()} м"
            distanceKm < 10 -> "${String.format("%.1f", distanceKm)} км"
            else -> "${distanceKm.toInt()} км"
        }
    }

    private fun estimateTravelTime(distanceKm: Double, mode: TravelMode): Int {
        val speedKmh = when (mode) {
            TravelMode.WALKING -> 5.0
            TravelMode.BICYCLING -> 15.0
            TravelMode.DRIVING -> 30.0
        }

        return (distanceKm / speedKmh * 60).toInt()
    }
}

data class LocationHotspot(
    val centerLat: Double,
    val centerLng: Double,
    val itemCount: Int,
    val items: List<Item>,
    val averageDistance: Double
)