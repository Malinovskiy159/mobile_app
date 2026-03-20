// ui/map/MapFragment.kt
package com.darim.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.darim.R
import com.darim.databinding.FragmentMapBinding
import com.darim.domain.model.Item
import com.darim.ui.MainActivity
import com.darim.ui.detail.DetailFragment
import com.darim.ui.shared.FilterViewModel
import com.darim.ui.utils.UserLocationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val TAG = "MapFragment"

    private lateinit var mapView: MapView
    private lateinit var mapObjects: MapObjectCollection
    private val markers = mutableMapOf<String, PlacemarkMapObject>()

    private val mapViewModel: MapViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

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

        MapKitFactory.initialize(requireContext())

        mapView = binding.mapView
        mapObjects = mapView.map.mapObjects.addCollection()

        setupMap()
        setupListeners()
        setupObservers()
        checkLocationPermission()
    }

    private fun setupMap() {
        // Устанавливаем начальную позицию карты (Москва)
        mapView.map.move(
            CameraPosition(
                Point(55.7558, 37.6176),
                12.0f,
                0.0f,
                0.0f
            )
        )

        // Настройка карты
        mapView.map.isRotateGesturesEnabled = true
        mapView.map.isZoomGesturesEnabled = true
        mapView.map.isScrollGesturesEnabled = true
        mapView.map.isTiltGesturesEnabled = true

        // Обработка кликов по карте
        mapView.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                binding.itemCard.visibility = View.GONE
                mapViewModel.clearSelectedItem()
            }

            override fun onMapLongTap(map: Map, point: Point) {}
        })
    }

    private fun setupListeners() {
        binding.fabMyLocation.setOnClickListener {
            moveToUserLocation()
        }

        binding.fabList.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabFilter.setOnClickListener {
            showFilterInfo()
        }

        binding.buttonViewDetails.setOnClickListener {
            val item = binding.itemCard.tag as? Item
            item?.let {
                openDetailFragment(it.id)
                binding.itemCard.visibility = View.GONE
            }
        }

        binding.buttonClose.setOnClickListener {
            binding.itemCard.visibility = View.GONE
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Наблюдаем за отфильтрованными вещами
                mapViewModel.filteredItems.observeForever { items ->
                    updateMarkers(items)
                    updateItemCount(items.size)
                }
            }
        }

        // Наблюдаем за выбранным маркером
        mapViewModel.selectedItemId.observe(viewLifecycleOwner) { itemId ->
            itemId?.let {
                val item = mapViewModel.filteredItems.value?.find { it.id == itemId }
                item?.let {
                    showItemInfo(it)
                }
            }
        }

        // Наблюдаем за состоянием геолокации
        mapViewModel.locationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MapViewModel.LocationState.Disabled -> {
                    Toast.makeText(requireContext(), "Включите геолокацию для лучшего опыта", Toast.LENGTH_LONG).show()
                }
                is MapViewModel.LocationState.PermissionDenied -> {
                    Toast.makeText(requireContext(), "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show()
                }
                is MapViewModel.LocationState.Error -> {
                    Toast.makeText(requireContext(), "Ошибка: ${state.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        // Наблюдаем за ошибками
        mapViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                mapViewModel.clearError()
            }
        }

        // Наблюдаем за фильтрами из FilterViewModel
        filterViewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            applyFilters()
        }

        filterViewModel.selectedCategories.observe(viewLifecycleOwner) { categories ->
            applyFilters()
        }

        filterViewModel.radius.observe(viewLifecycleOwner) { radius ->
            applyFilters()
        }

        filterViewModel.isWholeCity.observe(viewLifecycleOwner) { isWholeCity ->
            applyFilters()
        }

        filterViewModel.sortType.observe(viewLifecycleOwner) { sortType ->
            applyFilters()
        }
    }

    private fun applyFilters() {
        mapViewModel.updateFilters(
            searchQuery = filterViewModel.searchQuery.value ?: "",
            categories = filterViewModel.selectedCategories.value ?: emptySet(),
            radius = filterViewModel.radius.value ?: 5.0,
            isWholeCity = filterViewModel.isWholeCity.value ?: false,
            sortType = filterViewModel.sortType.value ?: "date"
        )
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

        // Цвета для разных категорий
        val categoryColors = mapOf(
            "Техника" to "#6200EE",
            "Книги" to "#FF9800",
            "Одежда" to "#2196F3",
            "Мебель" to "#4CAF50",
            "Детские товары" to "#FF4081",
            "Спорт" to "#00BCD4",
            "Инструменты" to "#795548",
            "Освещение" to "#FFC107",
            "Другое" to "#9E9E9E"
        )

        items.forEach { item ->
            val point = Point(item.location.lat, item.location.lng)
            val color = categoryColors[item.category] ?: "#6200EE"

            try {
                // Создаем маркер с цветом категории
                val markerImage = createColoredMarker(color)
                val marker = mapObjects.addPlacemark(
                    point,
                    markerImage,
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
                        mapViewModel.onMarkerClick(it.id)
                    }
                    true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error adding marker for ${item.title}: ${e.message}")
            }
        }
    }

    private fun createColoredMarker(colorHex: String): ImageProvider {
        return try {
            // Создаем маркер с цветом
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker_default)?.mutate()
            drawable?.setTint(Color.parseColor(colorHex))

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
                ImageProvider.fromResource(requireContext(), R.drawable.ic_marker_default)
            }
        } catch (e: Exception) {
            ImageProvider.fromResource(requireContext(), R.drawable.ic_marker_default)
        }
    }

    private fun updateItemCount(count: Int) {
        binding.textFilterInfo.text = "Найдено: $count объявлений"

        val categories = filterViewModel.selectedCategories.value
        if (!categories.isNullOrEmpty()) {
            binding.textFilterInfo.append(" | Категории: ${categories.joinToString(", ")}")
        }

        val radius = filterViewModel.radius.value
        if (filterViewModel.isWholeCity.value == false) {
            binding.textFilterInfo.append(" | Радиус: ${radius?.toInt()} км")
        }
    }

    private fun showFilterInfo() {
        val message = buildString {
            appendLine("📊 Текущие фильтры:")
            appendLine()

            val categories = filterViewModel.selectedCategories.value
            if (!categories.isNullOrEmpty()) {
                appendLine("Категории: ${categories.joinToString(", ")}")
            } else {
                appendLine("Категории: все")
            }

            if (filterViewModel.isWholeCity.value == true) {
                appendLine("Радиус: весь город")
            } else {
                appendLine("Радиус: ${filterViewModel.radius.value?.toInt()} км")
            }

            when (filterViewModel.sortType.value) {
                "distance" -> appendLine("Сортировка: по расстоянию")
                else -> appendLine("Сортировка: по дате")
            }

            val searchQuery = filterViewModel.searchQuery.value
            if (!searchQuery.isNullOrEmpty()) {
                appendLine("Поиск: \"$searchQuery\"")
            }
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showItemInfo(item: Item) {
        binding.itemCard.visibility = View.VISIBLE
        binding.itemCard.tag = item
        binding.itemTitle.text = item.title
        binding.itemCategory.text = item.category
        binding.itemDistance.text = "📍 ${item.location.address}"
    }

    private fun openDetailFragment(itemId: String) {
        val fragment = DetailFragment.newInstance(itemId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun moveToUserLocation() {
        val location = UserLocationManager.getLastKnownLocation()
        if (location != null) {
            moveCameraToLocation(location.lat, location.lng)
        } else {
            Toast.makeText(requireContext(), "Местоположение не определено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveCameraToLocation(lat: Double, lng: Double, zoom: Float = 15f) {
        mapView.map.move(
            CameraPosition(
                Point(lat, lng),
                zoom,
                0.0f,
                0.0f
            )
        )
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // Разрешение есть, получаем локацию через ViewModel
                mapViewModel.loadUserLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionDialog()
            }
            else -> {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun showLocationPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Разрешение на геолокацию")
            .setMessage("Для отображения вашего местоположения на карте и поиска объявлений рядом необходимо разрешение")
            .setPositiveButton("Разрешить") { _, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapViewModel.loadUserLocation()
                    Toast.makeText(requireContext(), "Геолокация доступна", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Для поиска рядом включите геолокацию в настройках", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}