// ui/myitems/MyItemsFragment.kt
package com.darim.ui.myitems

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.darim.data.repository.ItemRepositoryImpl
import com.darim.databinding.FragmentMyItemsBinding
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.repository.ItemRepository
import com.darim.ui.detail.DetailFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darim.R

class MyItemsFragment : Fragment() {

    private var _binding: FragmentMyItemsBinding? = null
    private val binding get() = _binding!!
    private val TAG = "MyItemsFragment"

    private lateinit var itemRepository: ItemRepository
    private lateinit var publishedAdapter: MyItemsAdapter
    private lateinit var bookedAdapter: MyItemsAdapter

    private var myPublishedItems = listOf<Item>()
    private var myBookedItems = listOf<Item>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализируем репозиторий
        itemRepository = ItemRepositoryImpl(requireContext())

        setupTabs()
        setupAdapters()
        setupSwipeRefresh()
        loadData()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showPublishedItems()
                    1 -> showBookedItems()
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupAdapters() {
        publishedAdapter = MyItemsAdapter(
            items = myPublishedItems,
            onItemClick = { item -> openDetailFragment(item) },
            onEditClick = { item -> editItem(item) },
            onDeleteClick = { item -> deleteItem(item) },
            onManageClick = { item -> manageBooking(item) }
        )

        bookedAdapter = MyItemsAdapter(
            items = myBookedItems,
            onItemClick = { item -> openDetailFragment(item) },
            onEditClick = { item -> editItem(item) },
            onDeleteClick = { item -> deleteItem(item) },
            onManageClick = { item -> manageBooking(item) }
        )

        binding.recyclerPublished.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = publishedAdapter
        }

        binding.recyclerBooked.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookedAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
        binding.swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary
        )
    }

    private fun loadData() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

            try {
                // Загружаем данные из репозитория
                val userId = "user1" // В реальном приложении - ID текущего пользователя

                val published = withContext(Dispatchers.IO) {
                    itemRepository.getMyItems(userId)
                }

                val booked = withContext(Dispatchers.IO) {
                    itemRepository.getMyBookings(userId)
                }

                myPublishedItems = published
                myBookedItems = booked

                Log.d(TAG, "Loaded ${myPublishedItems.size} published items")
                Log.d(TAG, "Loaded ${myBookedItems.size} booked items")

                // Обновляем адаптеры
                publishedAdapter.updateItems(myPublishedItems)
                bookedAdapter.updateItems(myBookedItems)

                showPublishedItems()
                updateStats()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                Toast.makeText(requireContext(), "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showPublishedItems() {
        binding.recyclerPublished.visibility = View.VISIBLE
        binding.recyclerBooked.visibility = View.GONE
        binding.emptyView.visibility = if (myPublishedItems.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.text = "У вас пока нет опубликованных вещей"
    }

    private fun showBookedItems() {
        binding.recyclerPublished.visibility = View.GONE
        binding.recyclerBooked.visibility = View.VISIBLE
        binding.emptyView.visibility = if (myBookedItems.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.text = "У вас пока нет забронированных вещей"
    }

    private fun updateStats() {
        val publishedCount = myPublishedItems.size
        val bookedCount = myBookedItems.size
        val availableCount = myPublishedItems.count { it.status == ItemStatus.AVAILABLE }
        val completedCount = myPublishedItems.count { it.status == ItemStatus.COMPLETED }

        binding.textStats.text = "📊 Опубликовано: $publishedCount | Доступно: $availableCount | Завершено: $completedCount | Забронировано: $bookedCount"
    }

    private fun openDetailFragment(item: Item) {
        val fragment = DetailFragment.newInstance(item)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun editItem(item: Item) {
        Toast.makeText(requireContext(), "Редактирование: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteItem(item: Item) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Удаление вещи")
            .setMessage("Вы уверены, что хотите удалить \"${item.title}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    itemRepository.deleteItem(item.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Вещь удалена", Toast.LENGTH_SHORT).show()
                        loadData() // Перезагружаем список
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun manageBooking(item: Item) {
        Toast.makeText(requireContext(), "Управление бронированием: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}