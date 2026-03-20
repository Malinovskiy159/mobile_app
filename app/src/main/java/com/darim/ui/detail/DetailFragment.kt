// ui/detail/DetailFragment.kt
package com.darim.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.darim.R
import com.darim.databinding.FragmentDetailBinding
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.ui.MainActivity
import com.darim.ui.utils.PhotoManager
import com.darim.ui.utils.SessionManager
import com.darim.ui.utils.UserLocationManager
import com.darim.ui.detail.DetailViewModel
import com.google.android.material.tabs.TabLayoutMediator
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private val TAG = "DetailFragment"

    private val viewModel: DetailViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private lateinit var photoAdapter: PhotoPagerAdapter
    private var currentItem: Item? = null
    private var currentItemId: String? = null
    private var currentUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация
        MapKitFactory.initialize(requireContext())
        currentUserId = SessionManager.getCurrentUserId()

        initViews()
        setupViewPager()
        setupToolbar()
        setupListeners()
        setupObservers()
        loadItemData()
    }

    private fun initViews() {
        photoAdapter = PhotoPagerAdapter()
    }

    private fun setupViewPager() {
        binding.viewPagerPhotos.adapter = photoAdapter

        TabLayoutMediator(binding.tabLayoutDots, binding.viewPagerPhotos) { tab, position ->
            // Точки без текста
        }.attach()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupListeners() {
        binding.bookButton.setOnClickListener {
            currentItem?.let { item ->
                if (viewModel.canBook.value == true) {
                    showBookingConfirmationDialog(item)
                } else {
                    Toast.makeText(requireContext(), "Вы не можете забронировать эту вещь", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.favoriteButton.setOnClickListener {
            toggleFavorite()
        }

        binding.sellerAvatar.setOnClickListener {
            showSellerProfile()
        }

        binding.sellerPhone.setOnClickListener {
            copyPhoneToClipboard()
        }

        binding.sellerName.setOnClickListener {
            showSellerProfile()
        }

        binding.buttonOpenMap.setOnClickListener {
            openInMaps()
        }

        binding.buttonShare.setOnClickListener {
            shareItem()
        }

        binding.editButton.setOnClickListener {
            currentItem?.let { item ->
                Toast.makeText(requireContext(), "Редактирование: ${item.title}", Toast.LENGTH_SHORT).show()
                // TODO: Open edit screen
            }
        }

        binding.deleteButton.setOnClickListener {
            currentItem?.let { item ->
                showDeleteConfirmationDialog(item)
            }
        }

        binding.buttonRetry.setOnClickListener {
            loadItemData()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Наблюдаем за данными вещи
                viewModel.item.observeForever { item ->
                    item?.let {
                        currentItem = it
                        displayItemDetails(it)
                        hideError()
                    }
                }

                // Наблюдаем за владельцем
                viewModel.owner.observeForever { owner ->
                    owner?.let {
                        displayOwnerInfo(it)
                    }
                }

                // Наблюдаем за возможностью бронирования
                viewModel.canBook.observeForever { canBook ->
                    updateBookButton(canBook)
                }

                // Наблюдаем за статусом владельца
                viewModel.isOwner.observeForever { isOwner ->
                    if (isOwner) {
                        binding.bookButton.visibility = View.GONE
                        binding.ownerActions.visibility = View.VISIBLE
                    } else {
                        binding.ownerActions.visibility = View.GONE
                        binding.bookButton.visibility = View.VISIBLE
                    }
                }

                // Наблюдаем за состоянием загрузки
                viewModel.isLoading.observeForever { isLoading ->
                    if (isLoading) {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.contentLayout.visibility = View.GONE
                        binding.errorLayout.visibility = View.GONE
                    } else {
                        binding.progressBar.visibility = View.GONE
                    }
                }

                // Наблюдаем за ошибками
                viewModel.error.observeForever { error ->
                    error?.let {
                        showError(it)
                        viewModel.clearError()
                    }
                }

                // Наблюдаем за результатом бронирования
                viewModel.bookingResult.observeForever { result ->
                    when (result) {
                        is DetailViewModel.BookingResult.Success -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                            viewModel.clearBookingResult()
                            // Обновляем данные после бронирования
                            currentItemId?.let { id ->
                                viewModel.loadItemDetails(id, currentUserId)
                            }
                        }
                        is DetailViewModel.BookingResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearBookingResult()
                        }
                        null -> {}
                    }
                }
            }
        }
    }

    private fun loadItemData() {
        // Получаем itemId из аргументов
        currentItemId = arguments?.getString("itemId")

        if (currentItemId != null) {
            Log.d(TAG, "Loading item with ID: $currentItemId")
            viewModel.loadItemDetails(currentItemId!!, currentUserId)
        } else {
            Log.e(TAG, "No item ID provided")
            showError("Не удалось загрузить объявление: отсутствует ID")
        }
    }

    private fun displayItemDetails(item: Item) {
        binding.contentLayout.visibility = View.VISIBLE

        binding.detailTitle.text = item.title
        binding.detailDescription.text = item.description
        binding.detailCategory.text = "Категория: ${item.category}"
        binding.detailLocation.text = item.location.address

        updateStatusBadge(item.status)

        binding.viewsText.text = formatViews(item.views)
        binding.timeText.text = formatTime(item.createdAt)

        calculateAndDisplayDistance(item.location)

        loadItemPhotos(item)

        setupMap(item.location)
    }

    private fun displayOwnerInfo(owner: com.darim.domain.model.User) {
        binding.sellerName.text = owner.name
        binding.sellerPhone.text = owner.phone
        binding.sellerRating.text = String.format("%.1f ★ (%d отзывов)", owner.rating, owner.reviews.size)
        binding.sellerStats.text = "Отдал: ${owner.itemsGiven} · Получил: ${owner.itemsTaken}"
    }

    private fun updateBookButton(canBook: Boolean) {
        if (canBook) {
            binding.bookButton.isEnabled = true
            binding.bookButton.text = "Заберу"
        } else {
            binding.bookButton.isEnabled = false
            binding.bookButton.text = when (currentItem?.status) {
                ItemStatus.AVAILABLE -> "Ваша вещь"
                ItemStatus.BOOKED -> "Уже забронировано"
                ItemStatus.COMPLETED -> "Уже забрали"
                ItemStatus.CANCELLED -> "Отменено"
                else -> "Недоступно"
            }
        }
    }

    private fun loadItemPhotos(item: Item) {
        if (item.photos.isEmpty()) {
            photoAdapter.submitList(listOf(null))
            binding.tabLayoutDots.visibility = View.GONE
        } else {
            val photoUris = item.photos.mapNotNull { photoPath ->
                PhotoManager.getPhotoUri(requireContext(), photoPath)
            }

            if (photoUris.isNotEmpty()) {
                photoAdapter.submitList(photoUris)
                binding.tabLayoutDots.visibility = if (photoUris.size > 1) View.VISIBLE else View.GONE
            } else {
                photoAdapter.submitList(listOf(null))
                binding.tabLayoutDots.visibility = View.GONE
            }
        }
    }

    private fun setupMap(location: Location) {
        try {
            binding.mapView.map.mapObjects.clear()

            binding.mapView.map.move(
                CameraPosition(
                    Point(location.lat, location.lng),
                    16.0f,
                    0.0f,
                    0.0f
                )
            )

            val markerImage = ImageProvider.fromResource(requireContext(), R.drawable.ic_marker_default)
            binding.mapView.map.mapObjects.addPlacemark(
                Point(location.lat, location.lng),
                markerImage,
                IconStyle().apply {
                    scale = 0.5f
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map", e)
        }
    }

    private fun updateStatusBadge(status: ItemStatus) {
        binding.statusBadge.visibility = View.VISIBLE
        binding.statusBadge.text = when (status) {
            ItemStatus.AVAILABLE -> "Доступно"
            ItemStatus.BOOKED -> "Забронировано"
            ItemStatus.COMPLETED -> "Завершено"
            ItemStatus.CANCELLED -> "Отменено"
        }

        val backgroundColor = when (status) {
            ItemStatus.AVAILABLE -> R.color.status_available
            ItemStatus.BOOKED -> R.color.status_booked
            ItemStatus.COMPLETED -> R.color.status_completed
            ItemStatus.CANCELLED -> R.color.status_cancelled
        }

        binding.statusBadge.setBackgroundColor(resources.getColor(backgroundColor, null))
    }

    private fun calculateAndDisplayDistance(itemLocation: Location) {
        val userLocation = UserLocationManager.getLastKnownLocation()

        val distance = if (userLocation != null) {
            calculateDistance(
                userLocation.lat, userLocation.lng,
                itemLocation.lat, itemLocation.lng
            )
        } else {
            1.2
        }

        binding.detailDistance.text = when {
            distance < 1 -> "📍 ${(distance * 1000).toInt()} м от вас"
            distance < 10 -> "📍 ${String.format("%.1f", distance)} км от вас"
            else -> "📍 ${distance.toInt()} км от вас"
        }
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371
        val latDistance = Math.toRadians(lat1 - lat2)
        val lonDistance = Math.toRadians(lng1 - lng2)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun toggleFavorite() {
        // TODO: Implement favorites
        Toast.makeText(requireContext(), "Добавлено в избранное", Toast.LENGTH_SHORT).show()
    }

    private fun shareItem() {
        currentItem?.let { item ->
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Посмотрите эту вещь: ${item.title}\n\n${item.description}\n\nНайдено в приложении Darim")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Поделиться через"))
        }
    }

    private fun showBookingConfirmationDialog(item: Item) {
        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение бронирования")
            .setMessage("Вы уверены, что хотите забрать эту вещь?\n\nПосле подтверждения вы сможете связаться с владельцем.")
            .setPositiveButton("Да, забрать") { _, _ ->
                currentUserId?.let { userId ->
                    viewModel.bookItem(item.id, userId)
                } ?: run {
                    Toast.makeText(requireContext(), "Необходимо войти в систему", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(item: Item) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление объявления")
            .setMessage("Вы уверены, что хотите удалить это объявление?")
            .setPositiveButton("Удалить") { _, _ ->
                // TODO: Implement delete
                Toast.makeText(requireContext(), "Объявление удалено", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSellerProfile() {
        currentItem?.let { item ->
            Toast.makeText(requireContext(), "Профиль продавца: ${item.ownerName ?: "Пользователь"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPhoneToClipboard() {
        binding.sellerPhone.text.toString().takeIf { it.isNotBlank() }?.let { phone ->
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("phone", phone)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInMaps() {
        currentItem?.let { item ->
            val uri = Uri.parse("yandexmaps://maps.yandex.ru/?pt=${item.location.lng},${item.location.lat}&z=16&l=map")
            val intent = Intent(Intent.ACTION_VIEW, uri)

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                val webUri = Uri.parse("https://yandex.ru/maps/?pt=${item.location.lng},${item.location.lat}&z=16&l=map")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                startActivity(webIntent)
            }
        }
    }

    private fun formatViews(views: Int): String {
        return when {
            views == 0 -> "👁 0 просмотров"
            views == 1 -> "👁 1 просмотр"
            views in 2..4 -> "👁 $views просмотра"
            else -> "👁 $views просмотров"
        }
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "🕒 только что"
            diff < 3_600_000 -> {
                val minutes = (diff / 60_000).toInt()
                "🕒 $minutes ${getMinutesText(minutes)} назад"
            }
            diff < 86_400_000 -> {
                val hours = (diff / 3_600_000).toInt()
                "🕒 $hours ${getHoursText(hours)} назад"
            }
            diff < 1_728_000_000 -> {
                val days = (diff / 86_400_000).toInt()
                "🕒 $days ${getDaysText(days)} назад"
            }
            else -> "🕒 ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))}"
        }
    }

    private fun getMinutesText(minutes: Int): String {
        return when {
            minutes % 10 == 1 && minutes % 100 != 11 -> "минуту"
            minutes % 10 in 2..4 && (minutes % 100 !in 12..14) -> "минуты"
            else -> "минут"
        }
    }

    private fun getHoursText(hours: Int): String {
        return when {
            hours % 10 == 1 && hours % 100 != 11 -> "час"
            hours % 10 in 2..4 && (hours % 100 !in 12..14) -> "часа"
            else -> "часов"
        }
    }

    private fun getDaysText(days: Int): String {
        return when {
            days % 10 == 1 && days % 100 != 11 -> "день"
            days % 10 in 2..4 && (days % 100 !in 12..14) -> "дня"
            else -> "дней"
        }
    }

    private fun showError(message: String) {
        binding.errorLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.errorText.text = message
        Log.e(TAG, "Error: $message")
    }

    private fun hideError() {
        binding.errorLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        binding.mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class PhotoPagerAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

        private var photos: List<Uri?> = emptyList()

        fun submitList(newPhotos: List<Uri?>) {
            photos = newPhotos
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_full, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.bind(photos[position])
        }

        override fun getItemCount() = photos.size

        inner class PhotoViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val imageView: android.widget.ImageView = itemView.findViewById(R.id.photoImageView)
            private val progressBar: View = itemView.findViewById(R.id.progressBar)

            fun bind(photoUri: Uri?) {
                if (photoUri != null) {
                    progressBar.visibility = View.GONE
                    loadFullImage(photoUri)
                } else {
                    progressBar.visibility = View.GONE
                    imageView.setImageResource(R.drawable.placeholder_image)
                }
            }

            private fun loadFullImage(uri: Uri) {
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading image", e)
                    imageView.setImageResource(R.drawable.error_image)
                }
            }
        }
    }

    companion object {
        fun newInstance(itemId: String): DetailFragment {
            val fragment = DetailFragment()
            val args = Bundle().apply {
                putString("itemId", itemId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}