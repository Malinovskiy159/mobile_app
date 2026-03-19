// ui/myitems/MyItemsFragment.kt
package com.darim.ui.myitems

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.darim.R
import com.darim.databinding.FragmentMyItemsBinding
import com.darim.domain.usecase.item.GetMyItemsUseCase
import com.darim.ui.MainActivity
import com.darim.ui.detail.DetailFragment
import com.darim.ui.publish.PublishFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class MyItemsFragment : Fragment() {

    private var _binding: FragmentMyItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyItemsViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private lateinit var myItemsAdapter: MyItemsAdapter
    private lateinit var myBookingsAdapter: MyBookingsAdapter

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

        setupToolbar()
        setupAdapters()
        setupTabs()
        setupSwipeRefresh()
        setupListeners()
        setupObservers()

        viewModel.refresh()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupAdapters() {
        // Адаптер для моих вещей
        myItemsAdapter = MyItemsAdapter(
            items = emptyList(),
            onItemClick = { item -> openDetailFragment(item.id) },
            onEditClick = { item -> editItem(item) },
            onDeleteClick = { item -> showDeleteConfirmation(item) },
            onManageClick = { item -> manageItem(item) }
        )

        binding.recyclerMyItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyItemsFragment.myItemsAdapter
        }

        // Адаптер для моих броней
        myBookingsAdapter = MyBookingsAdapter(
            bookings = emptyList(),
            onItemClick = { booking -> openDetailFragment(booking.item.id) },
            onContactClick = { booking -> contactOwner(booking) },
            onCancelClick = { booking -> cancelBooking(booking) },
            onViewDetailsClick = { booking -> viewBookingDetails(booking) }
        )

        binding.recyclerMyBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyItemsFragment.myBookingsAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showMyItemsTab()
                    1 -> showMyBookingsTab()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // По умолчанию показываем первую вкладку
        showMyItemsTab()
    }

    private fun showMyItemsTab() {
        binding.layoutMyItems.visibility = View.VISIBLE
        binding.layoutMyBookings.visibility = View.GONE
        updateEmptyState()
    }

    private fun showMyBookingsTab() {
        binding.layoutMyItems.visibility = View.GONE
        binding.layoutMyBookings.visibility = View.VISIBLE
        updateEmptyState()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.purple_500)
    }

    private fun setupListeners() {
        binding.buttonFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.buttonAddFirstItem.setOnClickListener {
            openPublishFragment()
        }
    }

    private fun setupObservers() {
        // Наблюдаем за моими вещами
        viewModel.myItems.observe(viewLifecycleOwner) { items ->
            myItemsAdapter.updateItems(items)
            updateEmptyState()
        }

        // Наблюдаем за моими бронями
        viewModel.myBookings.observe(viewLifecycleOwner) { bookings ->
            myBookingsAdapter.updateBookings(bookings)
            updateEmptyState()
        }

        // Наблюдаем за статистикой
        viewModel.myItemsStats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.textStats.text = it.getSummary()
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

    private fun updateEmptyState() {
        if (binding.layoutMyItems.visibility == View.VISIBLE) {
            val isEmpty = viewModel.myItems.value?.isEmpty() ?: true
            binding.recyclerMyItems.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.emptyMyItems.visibility = if (isEmpty) View.VISIBLE else View.GONE
        } else {
            val isEmpty = viewModel.myBookings.value?.isEmpty() ?: true
            binding.recyclerMyBookings.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.emptyMyBookings.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
    }

    private fun showFilterDialog() {
        val currentTab = if (binding.layoutMyItems.visibility == View.VISIBLE) 0 else 1

        if (currentTab == 0) {
            // Фильтр для моих вещей
            val items = arrayOf("Все", "Доступные", "Забронированные", "Завершенные", "Отмененные")
            val currentFilter = viewModel.myItemsFilter.value

            val checkedItem = when (currentFilter) {
                is GetMyItemsUseCase.ItemsFilter.All -> 0
                is GetMyItemsUseCase.ItemsFilter.Available -> 1
                is GetMyItemsUseCase.ItemsFilter.Booked -> 2
                is GetMyItemsUseCase.ItemsFilter.Completed -> 3
                is GetMyItemsUseCase.ItemsFilter.Cancelled -> 4
                else -> 0
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Фильтр моих вещей")
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    val filter = when (which) {
                        1 -> GetMyItemsUseCase.ItemsFilter.Available
                        2 -> GetMyItemsUseCase.ItemsFilter.Booked
                        3 -> GetMyItemsUseCase.ItemsFilter.Completed
                        4 -> GetMyItemsUseCase.ItemsFilter.Cancelled
                        else -> GetMyItemsUseCase.ItemsFilter.All
                    }
                    viewModel.setMyItemsFilter(filter)
                    dialog.dismiss()
                }
                .show()
        } else {
            // Фильтр для броней
            val items = arrayOf("Все", "Активные", "Предстоящие", "Завершенные", "Отмененные")
            val currentFilter = viewModel.bookingsFilter.value

            val checkedItem = when (currentFilter) {
                is com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.All -> 0
                is com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Active -> 1
                is com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Upcoming -> 2
                is com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Completed -> 3
                is com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Cancelled -> 4
                else -> 0
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Фильтр бронирований")
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    val filter = when (which) {
                        1 -> com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Active
                        2 -> com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Upcoming
                        3 -> com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Completed
                        4 -> com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.Cancelled
                        else -> com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingsFilter.All
                    }
                    viewModel.setBookingsFilter(filter)
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun openDetailFragment(itemId: String) {
        val fragment = DetailFragment.newInstance(itemId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openPublishFragment() {
        val fragment = PublishFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun editItem(item: com.darim.domain.model.Item) {
        Toast.makeText(requireContext(), "Редактирование: ${item.title}", Toast.LENGTH_SHORT).show()
        // TODO: Open edit screen
    }

    private fun showDeleteConfirmation(item: com.darim.domain.model.Item) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление вещи")
            .setMessage("Вы уверены, что хотите удалить \"${item.title}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteItem(item.id)
                Toast.makeText(requireContext(), "Вещь удалена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun manageItem(item: com.darim.domain.model.Item) {
        when (item.status) {
            com.darim.domain.model.ItemStatus.BOOKED -> {
                Toast.makeText(requireContext(), "Управление бронированием", Toast.LENGTH_SHORT).show()
                // TODO: Open transfer management
            }
            com.darim.domain.model.ItemStatus.COMPLETED -> {
                openDetailFragment(item.id)
            }
            else -> {
                openDetailFragment(item.id)
            }
        }
    }

    private fun contactOwner(booking: com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Связаться с владельцем")
            .setMessage("Имя: ${booking.ownerName}\nТелефон: ${booking.ownerPhone}")
            .setPositiveButton("Позвонить") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:${booking.ownerPhone}")
                }
                startActivity(intent)
            }
            .setNeutralButton("Скопировать телефон") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("phone", booking.ownerPhone)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun cancelBooking(booking: com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Отмена бронирования")
            .setMessage("Вы уверены, что хотите отменить бронирование?")
            .setPositiveButton("Отменить") { _, _ ->
                viewModel.cancelBooking(booking.item.id)
                Toast.makeText(requireContext(), "Бронирование отменено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Оставить", null)
            .show()
    }

    private fun viewBookingDetails(booking: com.darim.domain.usecase.item.GetMyBookingsUseCase.BookingItem) {
        if (booking.transfer != null) {
            // Если есть встреча, открываем экран управления встречей
            val fragment = com.darim.ui.transfer.TransferFragment.newInstance(booking.transfer.id)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        } else {
            // Если нет встречи, просто открываем детали вещи
            openDetailFragment(booking.item.id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}