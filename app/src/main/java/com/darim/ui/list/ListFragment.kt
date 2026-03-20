// ui/list/ListFragment.kt
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
import com.darim.databinding.FragmentListBinding
import com.darim.domain.model.Item
import com.darim.ui.MainActivity
import com.darim.ui.detail.DetailFragment
import com.darim.ui.utils.SessionManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ItemAdapter

    private val viewModel: ListViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        setupObservers()
        setupSearchView()
        setupFilterViews()
        setupAdapter()
        setupSwipeRefresh()

        // Слушаем изменения статуса из DetailFragment
        parentFragmentManager.setFragmentResultListener("itemStatusChanged", this) { _, bundle ->
            val itemId = bundle.getString("itemId")
            val newStatus = bundle.getSerializable("newStatus") as com.darim.domain.model.ItemStatus

            // Обновляем данные
            viewModel.refresh()
        }

        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Обновляем данные при возвращении на экран
        viewModel.refresh()
    }

    private fun initViews() {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun setupObservers() {
        // Наблюдаем за отфильтрованными вещами
        viewModel.filteredItems.observe(viewLifecycleOwner) { items ->
            adapter.updateItems(items)
            updateEmptyState(items.isEmpty())
        }

        // Наблюдаем за поисковым запросом
        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            if (binding.searchView.query.toString() != query) {
                binding.searchView.setQuery(query, false)
            }
        }

        // Наблюдаем за выбранными категориями
        viewModel.selectedCategories.observe(viewLifecycleOwner) { categories ->
            updateChipSelection(categories)
        }

        // Наблюдаем за радиусом
        viewModel.radius.observe(viewLifecycleOwner) { radius ->
            binding.radiusSeekBar.progress = radius.toInt()
            binding.radiusText.text = "$radius км"
        }

        // Наблюдаем за чекбоксом "Весь город"
        viewModel.isWholeCity.observe(viewLifecycleOwner) { isChecked ->
            binding.wholeCityCheckBox.isChecked = isChecked
            binding.radiusContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        // Наблюдаем за типом сортировки
        viewModel.sortType.observe(viewLifecycleOwner) { sortType ->
            when (sortType) {
                "distance" -> binding.sortRadioGroup.check(R.id.sortByDistance)
                else -> binding.sortRadioGroup.check(R.id.sortByDate)
            }
        }

        // Наблюдаем за состоянием загрузки
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        // Наблюдаем за ошибками
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupFilterViews() {
        // Кнопка фильтра - показывает/скрывает панель фильтров
        binding.filterButton.setOnClickListener {
            binding.filterContainer.visibility = if (binding.filterContainer.visibility == View.VISIBLE)
                View.GONE else View.VISIBLE
        }

        // SeekBar для радиуса
        binding.radiusSeekBar.max = 50
        binding.radiusSeekBar.progress = viewModel.radius.value?.toInt() ?: 5

        binding.radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.updateRadius(progress.toDouble())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Обработка чекбокса "Весь город"
        binding.wholeCityCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateWholeCity(isChecked)
        }

        // Обработка выбора сортировки
        binding.sortRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val sortType = when (checkedId) {
                R.id.sortByDistance -> "distance"
                else -> "date"
            }
            viewModel.updateSortType(sortType)
        }

        // Категории
        setupCategoryChips()

        // Кнопка применения фильтров
        binding.applyFilterButton.setOnClickListener {
            val selectedCats = collectSelectedCategories()
            viewModel.updateCategories(selectedCats)
            binding.filterContainer.visibility = View.GONE
        }

        // Кнопка сброса фильтров
        binding.clearFiltersButton.setOnClickListener {
            viewModel.clearFilters()
            clearChipSelection()
        }
    }

    private fun setupCategoryChips() {
        // В реальном приложении здесь должен быть запрос к репозиторию
        // Пока используем фиксированные категории
        val categories = listOf("Техника", "Книги", "Одежда", "Мебель",
            "Детские товары", "Спорт", "Инструменты", "Другое")

        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isClickable = true
            }
            binding.categoryChipGroup.addView(chip)
        }
    }

    private fun updateChipSelection(categories: Set<String>) {
        for (i in 0 until binding.categoryChipGroup.childCount) {
            val chip = binding.categoryChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = chip?.text in categories
        }
    }

    private fun clearChipSelection() {
        for (i in 0 until binding.categoryChipGroup.childCount) {
            val chip = binding.categoryChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = false
        }
    }

    private fun collectSelectedCategories(): Set<String> {
        val selected = mutableSetOf<String>()
        for (i in 0 until binding.categoryChipGroup.childCount) {
            val chip = binding.categoryChipGroup.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selected.add(chip.text.toString())
            }
        }
        return selected
    }

    private fun setupAdapter() {
        adapter = ItemAdapter(emptyList()) { item ->
            openDetailFragment(item.id)
        }
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.purple_500)
    }

    private fun openDetailFragment(itemId: String) {
        val fragment = DetailFragment.newInstance(itemId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // Разрешение уже есть, ничего не делаем
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
            .setMessage("Для отображения объявлений рядом с вами необходимо разрешение на определение местоположения")
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.loadUserLocation()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}