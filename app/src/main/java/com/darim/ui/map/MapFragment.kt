// ui/map/MapFragment.kt
package com.darim.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.darim.R
import com.darim.databinding.FragmentMapBinding
import com.darim.domain.model.Item
import com.darim.ui.detail.DetailFragment
import com.darim.ui.shared.FilterViewModel
import com.darim.ui.utils.UserLocationManager
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val TAG = "MapFragment"

    private lateinit var mapView: MapView
    private lateinit var mapObjects: MapObjectCollection
    private val markers = mutableMapOf<String, PlacemarkMapObject>()

    private val filterViewModel: FilterViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            MapKitFactory.initialize(requireContext())

            mapView = binding.mapView
            mapObjects = mapView.map.mapObjects.addCollection()

            setupMap()
            setupListeners()
            observeUserLocation()
            observeFilteredItems()

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка загрузки карты", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMap() {
        mapView.map.move(
            CameraPosition(
                Point(55.7558, 37.6176),
                12.0f,
                0.0f,
                0.0f
            )
        )

        // Настройка стилей карты
        mapView.map.isRotateGesturesEnabled = true
        mapView.map.isZoomGesturesEnabled = true
        mapView.map.isScrollGesturesEnabled = true
        mapView.map.isTiltGesturesEnabled = true
    }

    private fun setupListeners() {
        binding.fabMyLocation.setOnClickListener {
            moveToUserLocation()
        }

        binding.fabList.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabFilter.setOnClickListener {
            // Показываем текущие фильтры
            showFilterInfo()
        }

        mapView.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                binding.itemCard.visibility = View.GONE
            }

            override fun onMapLongTap(map: Map, point: Point) {}
        })
    }

    private fun observeUserLocation() {
        UserLocationManager.userLocation.observe(viewLifecycleOwner) { location ->
            location?.let {
                moveCameraToLocation(it)
            }
        }
    }

    private fun observeFilteredItems() {
        filterViewModel.filteredItems.observe(viewLifecycleOwner) { items ->
            updateMarkers(items)
            updateFilterInfo(items.size)
        }
    }

    private fun updateMarkers(items: List<Item>) {
        // Очищаем старые маркеры
        markers.values.forEach { mapObjects.remove(it) }
        markers.clear()

        if (items.isEmpty()) {
            binding.textNoItems.visibility = View.VISIBLE
            return
        }

        binding.textNoItems.visibility = View.GONE

        // Получаем изображения для маркеров разных категорий
        val markerImages = mapOf(
            "Техника" to getMarkerImage(R.color.purple_500),
            "Книги" to getMarkerImage(R.color.status_booked),
            "Одежда" to getMarkerImage(R.color.status_completed),
            "Мебель" to getMarkerImage(R.color.status_available),
            "Детские товары" to getMarkerImage(R.color.rating),
            "Спорт" to getMarkerImage(R.color.teal_200),
            "Инструменты" to getMarkerImage(R.color.purple_700),
            "Освещение" to getMarkerImage(R.color.warning),
            "Другое" to getMarkerImage(R.color.gray)
        )

        items.forEach { item ->
            val point = Point(item.location.lat, item.location.lng)
            val image = markerImages[item.category] ?: markerImages["Другое"]!!

            try {
                val marker = mapObjects.addPlacemark(
                    point,
                    image,
                    IconStyle().apply {
                        scale = 0.5f
                        zIndex = 0f
                    }
                )

                marker.userData = item
                markers[item.id] = marker

                marker.addTapListener { mapObject, point ->
                    val clickedItem = mapObject.userData as? Item
                    clickedItem?.let {
                        showItemInfo(it)
                    }
                    true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error adding marker for ${item.title}: ${e.message}")
            }
        }
    }

    private fun getMarkerImage(colorResId: Int): ImageProvider {
        return try {
            // Создаем маркер с цветом
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker_default)?.mutate()
            drawable?.setTint(ContextCompat.getColor(requireContext(), colorResId))

            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                ImageProvider.fromBitmap(bitmap)
            } else {
                ImageProvider.fromResource(requireContext(), android.R.drawable.ic_dialog_map)
            }
        } catch (e: Exception) {
            ImageProvider.fromResource(requireContext(), android.R.drawable.ic_dialog_map)
        }
    }

    private fun updateFilterInfo(itemCount: Int) {
        binding.textFilterInfo.text = "Найдено: $itemCount объявлений"

        val categories = filterViewModel.selectedCategories.value
        if (!categories.isNullOrEmpty()) {
            binding.textFilterInfo.append(" | Категории: ${categories.joinToString()}")
        }

        val radius = filterViewModel.radius.value
        if (filterViewModel.isWholeCity.value == false) {
            binding.textFilterInfo.append(" | Радиус: ${radius?.toInt()} км")
        }
    }

    private fun showFilterInfo() {
        val message = buildString {
            append("📊 Текущие фильтры:\n\n")

            val categories = filterViewModel.selectedCategories.value
            if (!categories.isNullOrEmpty()) {
                append("Категории: ${categories.joinToString()}\n")
            } else {
                append("Категории: все\n")
            }

            if (filterViewModel.isWholeCity.value == true) {
                append("Радиус: весь город\n")
            } else {
                append("Радиус: ${filterViewModel.radius.value?.toInt()} км\n")
            }

            when (filterViewModel.sortType.value) {
                "distance" -> append("Сортировка: по расстоянию\n")
                else -> append("Сортировка: по дате\n")
            }

            val searchQuery = filterViewModel.searchQuery.value
            if (!searchQuery.isNullOrEmpty()) {
                append("Поиск: \"$searchQuery\"\n")
            }
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showItemInfo(item: Item) {
        binding.itemCard.visibility = View.VISIBLE
        binding.itemTitle.text = item.title
        binding.itemCategory.text = item.category
        binding.itemDistance.text = "📍 ${item.location.address}"

        binding.buttonViewDetails.setOnClickListener {
            openDetailFragment(item)
            binding.itemCard.visibility = View.GONE
        }

        binding.buttonClose.setOnClickListener {
            binding.itemCard.visibility = View.GONE
        }
    }

    private fun openDetailFragment(item: Item) {
        val fragment = DetailFragment.newInstance(item.id)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun moveToUserLocation() {
        val location = UserLocationManager.getLastKnownLocation()
        if (location != null) {
            moveCameraToLocation(location)
        } else {
            Toast.makeText(requireContext(), "Местоположение не определено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveCameraToLocation(location: com.darim.domain.model.Location) {
        mapView.map.move(
            CameraPosition(
                Point(location.lat, location.lng),
                15.0f,
                0.0f,
                0.0f
            )
        )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}