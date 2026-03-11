package com.darim.ui.list

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.data.repository.LocationRepositoryImpl
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.ui.detail.DetailFragment
import com.darim.ui.utils.UserLocationManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class ListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter
    private lateinit var searchView: SearchView
    private lateinit var filterButton: ImageButton
    private lateinit var filterContainer: LinearLayout
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var radiusSeekBar: SeekBar
    private lateinit var radiusText: TextView
    private lateinit var wholeCityCheckBox: CheckBox
    private lateinit var radiusContainer: LinearLayout
    private lateinit var applyFilterButton: Button
    private lateinit var emptyView: TextView
    private lateinit var sortRadioGroup: RadioGroup

    private val viewModel: ListViewModel by viewModels()
    private val locationRepository by lazy {
        LocationRepositoryImpl(requireContext())
    }

    // Исходные данные с номерами телефонов в каждом item
    private val allItems = listOf(
        Item(
            id = "1",
            title = "Кухонный комбайн Braun",
            category = "Техника",
            description = "Почти новый, использовался пару раз. Все насадки в комплекте. Самовывоз от метро Охотный ряд.",
            photos = emptyList(),
            location = Location(55.7558, 37.6176, "м. Охотный ряд"),
            ownerId = "user1",
            phone = "+7 (999) 123-45-67",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 86400000
        ),
        Item(
            id = "2",
            title = "Детские книжки (20 штук)",
            category = "Книги",
            description = "Для детей 3-5 лет. Сказки, раскраски, обучающие.",
            photos = emptyList(),
            location = Location(55.7512, 37.6289, "м. Китай-город"),
            ownerId = "user2",
            phone = "+7 (999) 234-56-78",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 172800000
        ),
        Item(
            id = "3",
            title = "Зимнее пальто, размер 46",
            category = "Одежда",
            description = "Носила один сезон, состояние отличное. Торг уместен.",
            photos = emptyList(),
            location = Location(55.7602, 37.6195, "м. Лубянка"),
            ownerId = "user1",
            phone = "+7 (999) 123-45-67",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 43200000
        ),
        Item(
            id = "4",
            title = "Настольная лампа",
            category = "Освещение",
            description = "С регулировкой яркости, светодиодная.",
            photos = emptyList(),
            location = Location(55.7587, 37.6153, "м. Театральная"),
            ownerId = "user3",
            phone = "+7 (999) 345-67-89",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis()
        ),
        Item(
            id = "5",
            title = "Велосипед детский",
            category = "Детские товары",
            description = "Для ребенка 3-5 лет, колеса 12 дюймов.",
            photos = emptyList(),
            location = Location(55.7492, 37.6215, "м. Лубянка"),
            ownerId = "user4",
            phone = "+7 (999) 456-78-90",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 259200000
        ),
        Item(
            id = "6",
            title = "Микроволновка Samsung",
            category = "Техника",
            description = "Рабочая, 20 литров, гриль.",
            photos = emptyList(),
            location = Location(55.7634, 37.6102, "м. Охотный ряд"),
            ownerId = "user5",
            phone = "+7 (999) 567-89-01",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 43200000
        ),
        Item(
            id = "7",
            title = "Книги по программированию",
            category = "Книги",
            description = "5 книг: Kotlin, Java, Python.",
            photos = emptyList(),
            location = Location(55.7523, 37.6251, "м. Китай-город"),
            ownerId = "user2",
            phone = "+7 (999) 234-56-78",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 604800000
        ),
        Item(
            id = "8",
            title = "Кресло компьютерное",
            category = "Мебель",
            description = "Офисное кресло, регулировка высоты.",
            photos = emptyList(),
            location = Location(55.7578, 37.6123, "м. Театральная"),
            ownerId = "user6",
            phone = "+7 (999) 678-90-12",
            status = ItemStatus.AVAILABLE,
            createdAt = System.currentTimeMillis() - 86400000
        )
    )

    // Отфильтрованные данные
    private var filteredItems = allItems.toList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupObservers()
        setupSearchView()
        setupFilterViews()
        setupAdapter()

        // Слушаем изменения статуса из DetailFragment
        parentFragmentManager.setFragmentResultListener("itemStatusChanged", this) { _, bundle ->
            val itemId = bundle.getString("itemId")
            val newStatus = bundle.getSerializable("newStatus") as ItemStatus

            val index = allItems.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val updatedItem = allItems[index].copy(status = newStatus)
                val mutableList = allItems.toMutableList()
                mutableList[index] = updatedItem
                applyFilters()
            }
        }

        lifecycleScope.launch {
            UserLocationManager.userLocation.collect { location ->
                applyFilters()
            }
        }

        checkLocationPermission()

        // Восстанавливаем состояние UI из ViewModel
        restoreUiState()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        searchView = view.findViewById(R.id.searchView)
        filterButton = view.findViewById(R.id.filterButton)
        filterContainer = view.findViewById(R.id.filterContainer)
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup)
        radiusSeekBar = view.findViewById(R.id.radiusSeekBar)
        radiusText = view.findViewById(R.id.radiusText)
        wholeCityCheckBox = view.findViewById(R.id.wholeCityCheckBox)
        radiusContainer = view.findViewById(R.id.radiusContainer)
        applyFilterButton = view.findViewById(R.id.applyFilterButton)
        emptyView = view.findViewById(R.id.emptyView)
        sortRadioGroup = view.findViewById(R.id.sortRadioGroup)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun setupObservers() {
        // Наблюдаем за изменениями в ViewModel
        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            if (searchView.query.toString() != query) {
                searchView.setQuery(query, false)
            }
        }

        viewModel.selectedCategories.observe(viewLifecycleOwner) { categories ->
            updateChipSelection(categories)
        }

        viewModel.radius.observe(viewLifecycleOwner) { radius ->
            radiusSeekBar.progress = radius.toInt()
            radiusText.text = "$radius км"
        }

        viewModel.isWholeCity.observe(viewLifecycleOwner) { isChecked ->
            wholeCityCheckBox.isChecked = isChecked
            radiusContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        viewModel.sortType.observe(viewLifecycleOwner) { sortType ->
            when (sortType) {
                "distance" -> sortRadioGroup.check(R.id.sortByDistance)
                else -> sortRadioGroup.check(R.id.sortByDate)
            }
        }
    }

    private fun restoreUiState() {
        // Восстанавливаем чипсы
        viewModel.selectedCategories.value?.let { categories ->
            updateChipSelection(categories)
        }

        // Применяем фильтры с сохраненным состоянием
        applyFilters()
    }

    private fun updateChipSelection(categories: Set<String>) {
        for (i in 0 until categoryChipGroup.childCount) {
            val chip = categoryChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = chip?.text in categories
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateSearchQuery(newText ?: "")
                applyFilters()
                return true
            }
        })
    }

    private fun setupFilterViews() {
        // Кнопка фильтра - показывает/скрывает панель фильтров
        filterButton.setOnClickListener {
            filterContainer.visibility = if (filterContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // SeekBar для радиуса
        radiusSeekBar.max = 50
        radiusSeekBar.progress = viewModel.radius.value?.toInt() ?: 5

        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.updateRadius(progress.toDouble())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Обработка чекбокса "Весь город"
        wholeCityCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateWholeCity(isChecked)
        }

        // Обработка выбора сортировки
        sortRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val sortType = when (checkedId) {
                R.id.sortByDistance -> "distance"
                else -> "date"
            }
            viewModel.updateSortType(sortType)
            applyFilters()
        }

        // Категории - динамически из allItems
        setupCategoryChips()

        // Кнопка применения фильтров
        applyFilterButton.setOnClickListener {
            val selectedCats = collectSelectedCategories()
            viewModel.updateCategories(selectedCats)
            applyFilters()
            filterContainer.visibility = View.GONE
        }
    }

    private fun setupCategoryChips() {
        categoryChipGroup.removeAllViews()
        val uniqueCategories = allItems.map { it.category }.distinct()

        uniqueCategories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isClickable = true
            }
            categoryChipGroup.addView(chip)
        }
    }

    private fun collectSelectedCategories(): Set<String> {
        val selected = mutableSetOf<String>()
        for (i in 0 until categoryChipGroup.childCount) {
            val chip = categoryChipGroup.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selected.add(chip.text.toString())
            }
        }
        return selected
    }

    private fun setupAdapter() {
        adapter = ItemAdapter(filteredItems) { item ->
            val detailFragment = DetailFragment.newInstance(item)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = adapter
    }

    private fun applyFilters() {
        var result = allItems

        // 1. Поиск по тексту
        val searchQuery = viewModel.searchQuery.value ?: ""
        if (searchQuery.isNotEmpty()) {
            result = result.filter { item ->
                item.title.lowercase().contains(searchQuery.lowercase()) ||
                        item.description.lowercase().contains(searchQuery.lowercase())
            }
        }

        // 2. Фильтр по категориям
        val categories = viewModel.selectedCategories.value ?: emptySet()
        if (categories.isNotEmpty()) {
            result = result.filter { item ->
                item.category in categories
            }
        }

        // 3. Фильтр по расстоянию
        val userLocation = UserLocationManager.userLocation.value
        if (userLocation != null) {
            if (!viewModel.isWholeCity.value!!) {
                val radius = viewModel.radius.value ?: 5.0
                result = result.filter { item ->
                    val distance = userLocation.distanceTo(item.location)
                    distance <= radius
                }
            }

            // Сортировка
            when (viewModel.sortType.value) {
                "distance" -> {
                    result = result.sortedBy { item ->
                        userLocation.distanceTo(item.location)
                    }
                }
                else -> {
                    result = result.sortedByDescending { it.createdAt }
                }
            }
        }

        filteredItems = result
        adapter.updateItems(filteredItems)

        emptyView.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filteredItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getUserLocation()
            }
            else -> {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun getUserLocation() {
        lifecycleScope.launch {
            locationRepository.getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}