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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.ui.MainActivity
import com.darim.ui.detail.DetailFragment
import com.darim.ui.utils.FavoritesManager
import com.darim.ui.utils.UserLocationManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class ListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter
    private lateinit var searchView: SearchView
    private lateinit var filterButton: ImageButton
    private lateinit var filterContainer: LinearLayout
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var applyFilterButton: Button
    private lateinit var emptyView: TextView

    private val viewModel: ListViewModel by viewModels()

    // Возвращаем полный список (8 предметов) с правильными полями (ownerPhone, ownerName)
    private val allItems = listOf(
        Item("1", "Кухонный комбайн Braun", "Техника", "Почти новый, все насадки.", emptyList(), Location(55.7558, 37.6176, "м. Охотный ряд"), "user1", "Алексей", "+7 (999) 123-45-67", ItemStatus.AVAILABLE, System.currentTimeMillis() - 86400000),
        Item("2", "Детские книжки (20 шт)", "Книги", "Сказки и раскраски.", emptyList(), Location(55.7512, 37.6289, "м. Китай-город"), "user2", "Мария", "+7 (999) 234-56-78", ItemStatus.AVAILABLE, System.currentTimeMillis() - 172800000),
        Item("3", "Зимнее пальто, р. 46", "Одежда", "Состояние отличное.", emptyList(), Location(55.7602, 37.6195, "м. Лубянка"), "user1", "Алексей", "+7 (999) 123-45-67", ItemStatus.AVAILABLE, System.currentTimeMillis() - 43200000),
        Item("4", "Настольная лампа", "Освещение", "Светодиодная.", emptyList(), Location(55.7587, 37.6153, "м. Театральная"), "user3", "Игорь", "+7 (999) 345-67-89", ItemStatus.AVAILABLE, System.currentTimeMillis()),
        Item("5", "Велосипед детский", "Детские товары", "Для ребенка 3-5 лет.", emptyList(), Location(55.7492, 37.6215, "м. Лубянка"), "user4", "Анна", "+7 (999) 456-78-90", ItemStatus.AVAILABLE, System.currentTimeMillis() - 259200000),
        Item("6", "Микроволновка Samsung", "Техника", "Рабочая, 20 литров.", emptyList(), Location(55.7634, 37.6102, "м. Охотный ряд"), "user5", "Дмитрий", "+7 (999) 567-89-01", ItemStatus.AVAILABLE, System.currentTimeMillis() - 43200000),
        Item("7", "Книги по Kotlin", "Книги", "5 книг для обучения.", emptyList(), Location(55.7523, 37.6251, "м. Китай-город"), "user2", "Мария", "+7 (999) 234-56-78", ItemStatus.AVAILABLE, System.currentTimeMillis() - 604800000),
        Item("8", "Кресло компьютерное", "Мебель", "Офисное, регулировка высоты.", emptyList(), Location(55.7578, 37.6123, "м. Театральная"), "user6", "Сергей", "+7 (999) 678-90-12", ItemStatus.AVAILABLE, System.currentTimeMillis() - 86400000)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupSearchView()
        setupFilterViews()
        setupAdapter()

        // Обновляем список, если вернулись из деталей (могли поставить лайк или забронировать)
        applyFilters()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        searchView = view.findViewById(R.id.searchView)
        filterButton = view.findViewById(R.id.filterButton)
        filterContainer = view.findViewById(R.id.filterContainer)
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup)
        applyFilterButton = view.findViewById(R.id.applyFilterButton)
        emptyView = view.findViewById(R.id.emptyView)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
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
        // ОЖИВЛЯЕМ КНОПКУ ФИЛЬТРОВ
        filterButton.setOnClickListener {
            filterContainer.visibility = if (filterContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Динамически создаем чипсы категорий
        categoryChipGroup.removeAllViews()
        val uniqueCategories = allItems.map { it.category }.distinct()
        uniqueCategories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = viewModel.selectedCategories.value?.contains(category) == true
            }
            categoryChipGroup.addView(chip)
        }

        applyFilterButton.setOnClickListener {
            val selected = mutableSetOf<String>()
            for (i in 0 until categoryChipGroup.childCount) {
                val chip = categoryChipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) selected.add(chip.text.toString())
            }
            viewModel.updateCategories(selected)
            applyFilters()
            filterContainer.visibility = View.GONE
        }
    }

    private fun setupAdapter() {
        adapter = ItemAdapter(emptyList()) { item ->
            val detailFragment = DetailFragment.newInstance(item)
            (activity as? MainActivity)?.loadFragment(detailFragment, true)
        }
        recyclerView.adapter = adapter
    }

    private fun applyFilters() {
        var result = allItems

        // 1. Фильтр ИЗБРАННОГО
        val showOnlyFavorites = arguments?.getBoolean("showOnlyFavorites", false) ?: false
        if (showOnlyFavorites) {
            val favIds = FavoritesManager.getFavoriteIds(requireContext())
            result = result.filter { it.id in favIds }
        }

        // 2. Фильтр ПОИСКА
        val query = viewModel.searchQuery.value ?: ""
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        // 3. Фильтр КАТЕГОРИЙ
        val selectedCats = viewModel.selectedCategories.value ?: emptySet()
        if (selectedCats.isNotEmpty()) {
            result = result.filter { it.category in selectedCats }
        }

        adapter.updateItems(result)

        // Обработка пустого состояния
        if (result.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = if (showOnlyFavorites) "В избранном пока пусто" else "Ничего не найдено"
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
}