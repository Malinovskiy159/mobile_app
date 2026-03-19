// ui/detail/DetailFragment.kt
package com.darim.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.darim.R
import com.darim.databinding.FragmentDetailBinding
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.domain.model.Location
import com.darim.ui.utils.PhotoManager
import com.darim.ui.utils.UserLocationManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayoutMediator
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlin.math.*

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoAdapter: PhotoPagerAdapter
    private var currentItem: Item? = null
    private var isFavorite = false

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

        // Инициализируем MapKit
        MapKitFactory.initialize(requireContext())

        initViews()
        setupViewPager()
        setupToolbar()
        setupListeners()
        loadItemData()
    }

    private fun initViews() {
        // Инициализация адаптера для фото
        photoAdapter = PhotoPagerAdapter()
    }

    private fun setupViewPager() {
        binding.viewPagerPhotos.adapter = photoAdapter

        // Индикатор страниц (точки)
        TabLayoutMediator(binding.tabLayoutDots, binding.viewPagerPhotos) { tab, position ->
            // Просто точки, без текста
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
                when {
                    item.status == ItemStatus.AVAILABLE -> {
                        showBookingConfirmationDialog(item)
                    }
                    item.status == ItemStatus.BOOKED && item.bookedBy == getCurrentUserId() -> {
                        showManageBookingDialog(item)
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Эта вещь уже недоступна", Toast.LENGTH_SHORT).show()
                    }
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
            e.printStackTrace()
        }
    }

    private fun loadItemData() {
        val item = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable("item", Item::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable("item") as? Item
        }

        if (item != null) {
            displayItem(item)
        } else {
            Toast.makeText(requireContext(), "Ошибка загрузки объявления", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun displayItem(item: Item) {
        currentItem = item

        // Основная информация
        binding.detailTitle.text = item.title
        binding.detailDescription.text = item.description
        binding.detailCategory.text = "Категория: ${item.category}"
        binding.detailLocation.text = item.location.address

        // Информация о продавце
        binding.sellerName.text = item.ownerName ?: "Продавец"
        binding.sellerPhone.text = item.ownerPhone ?: "+7 (999) 123-45-67"

        // Статус вещи
        updateStatusBadge(item.status)

        // Просмотры и время
        binding.viewsText.text = formatViews(item.views)
        binding.timeText.text = formatTime(item.createdAt)

        // Расстояние
        calculateAndDisplayDistance(item.location)

        // Загружаем фотографии
        loadItemPhotos(item)

        // Аватар продавца
        binding.sellerAvatar.setImageResource(R.drawable.ic_default_avatar)

        // Настраиваем карту
        setupMap(item.location)

        // Обновляем кнопку в зависимости от владельца
        updateButtonForUser(item)

        // Проверяем, не в избранном ли вещь
        checkIfFavorite(item.id)
    }

    private fun updateButtonForUser(item: Item) {
        val currentUserId = getCurrentUserId()
        val isOwner = item.ownerId == currentUserId

        when {
            isOwner -> {
                // Это моя вещь - показываем другие кнопки
                binding.bookButton.visibility = View.GONE
                binding.ownerActions.visibility = View.VISIBLE
                binding.editButton.visibility = View.VISIBLE
                binding.deleteButton.visibility = View.VISIBLE
            }
            item.status == ItemStatus.AVAILABLE -> {
                // Чужая доступная вещь
                binding.bookButton.visibility = View.VISIBLE
                binding.bookButton.isEnabled = true
                binding.bookButton.text = "Заберу"
                binding.ownerActions.visibility = View.GONE
            }
            item.status == ItemStatus.BOOKED && item.bookedBy == currentUserId -> {
                // Я забронировал эту вещь
                binding.bookButton.visibility = View.VISIBLE
                binding.bookButton.isEnabled = true
                binding.bookButton.text = "Управление бронированием"
                binding.ownerActions.visibility = View.GONE
            }
            else -> {
                // Вещь недоступна
                binding.bookButton.visibility = View.VISIBLE
                binding.bookButton.isEnabled = false
                binding.bookButton.text = "Уже забрали"
                binding.ownerActions.visibility = View.GONE
            }
        }
    }

    private fun showBookingConfirmationDialog(item: Item) {
        // Дополнительная проверка перед диалогом
        if (item.ownerId == getCurrentUserId()) {
            Toast.makeText(requireContext(), "Вы не можете забронировать свою вещь", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение бронирования")
            .setMessage("Вы уверены, что хотите забрать эту вещь?")
            .setPositiveButton("Да, забрать") { _, _ ->
                confirmBooking(item)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadItemPhotos(item: Item) {
        if (item.photos.isEmpty()) {
            // Если нет фото, показываем заглушку
            val placeholderList = listOf<Uri?>(null)
            photoAdapter.submitList(placeholderList)
            binding.tabLayoutDots.visibility = View.GONE
        } else {
            // Загружаем все фото через PhotoManager
            val photoUris = item.photos.mapNotNull { photoPath ->
                PhotoManager.getPhotoUri(requireContext(), photoPath)
            }

            if (photoUris.isNotEmpty()) {
                photoAdapter.submitList(photoUris)
                binding.tabLayoutDots.visibility = if (photoUris.size > 1) View.VISIBLE else View.GONE
            } else {
                // Если фото не найдены, показываем заглушку
                val placeholderList = listOf<Uri?>(null)
                photoAdapter.submitList(placeholderList)
                binding.tabLayoutDots.visibility = View.GONE
            }
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

    private fun updateButtonForStatus(status: ItemStatus) {
        when {
            status == ItemStatus.AVAILABLE -> {
                binding.bookButton.isEnabled = true
                binding.bookButton.text = "Заберу"
            }
            status == ItemStatus.BOOKED && currentItem?.bookedBy == getCurrentUserId() -> {
                binding.bookButton.isEnabled = true
                binding.bookButton.text = "Управление"
            }
            else -> {
                binding.bookButton.isEnabled = false
                binding.bookButton.text = "Уже забрали"
            }
        }
    }

    private fun calculateAndDisplayDistance(itemLocation: Location) {
        val userLocation = UserLocationManager.getLastKnownLocation()

        val distance = if (userLocation != null) {
            calculateDistance(
                userLocation.lat, userLocation.lng,
                itemLocation.lat, itemLocation.lng
            )
        } else {
            1.2 // Заглушка
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
        isFavorite = !isFavorite
        if (isFavorite) {
            binding.favoriteButton.setImageResource(android.R.drawable.btn_star_big_on)
            Toast.makeText(requireContext(), "Добавлено в избранное", Toast.LENGTH_SHORT).show()
        } else {
            binding.favoriteButton.setImageResource(android.R.drawable.btn_star_big_off)
            Toast.makeText(requireContext(), "Удалено из избранного", Toast.LENGTH_SHORT).show()
        }
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

    private fun showManageBookingDialog(item: Item) {
        val options = arrayOf("Связаться с продавцом", "Отменить бронирование")

        AlertDialog.Builder(requireContext())
            .setTitle("Управление бронированием")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showContactDialog(item)
                    1 -> showCancelBookingDialog(item)
                }
            }
            .show()
    }

    private fun showCancelBookingDialog(item: Item) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена бронирования")
            .setMessage("Вы уверены, что хотите отменить бронирование?")
            .setPositiveButton("Отменить") { _, _ ->
                cancelBooking(item)
            }
            .setNegativeButton("Оставить", null)
            .show()
    }

    private fun confirmBooking(item: Item) {
        val updatedItem = item.copy(status = ItemStatus.BOOKED, bookedBy = getCurrentUserId())
        currentItem = updatedItem

        val bundle = Bundle().apply {
            putString("itemId", item.id)
            putSerializable("newStatus", ItemStatus.BOOKED)
        }
        parentFragmentManager.setFragmentResult("itemStatusChanged", bundle)

        updateButtonForStatus(ItemStatus.BOOKED)
        showContactDialog(item)
    }

    private fun cancelBooking(item: Item) {
        Toast.makeText(requireContext(), "Бронирование отменено", Toast.LENGTH_SHORT).show()
        updateButtonForStatus(ItemStatus.AVAILABLE)
    }

    private fun showContactDialog(item: Item) {
        val phoneNumber = item.ownerPhone ?: "+7 (999) 123-45-67"

        AlertDialog.Builder(requireContext())
            .setTitle("Вещь забронирована! 🎉")
            .setMessage("Продавец: ${item.ownerName ?: "Продавец"}\nТелефон: $phoneNumber")
            .setPositiveButton("Позвонить") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            }
            .setNeutralButton("Скопировать телефон") { _, _ ->
                copyTextToClipboard(phoneNumber, "Телефон скопирован")
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showSellerProfile() {
        currentItem?.let { item ->
            Toast.makeText(requireContext(), "Профиль продавца: ${item.ownerName ?: "Пользователь"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPhoneToClipboard() {
        binding.sellerPhone.text.toString().takeIf { it.isNotBlank() }?.let { phone ->
            copyTextToClipboard(phone, "Телефон скопирован")
        }
    }

    private fun copyTextToClipboard(text: String, message: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
            else -> "🕒 ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}"
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

    private fun checkIfFavorite(itemId: String) {
        isFavorite = false
        binding.favoriteButton.setImageResource(android.R.drawable.btn_star_big_off)
    }

    private fun getCurrentUserId(): String {
        return "user1"
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

    // Адаптер для ViewPager с фотографиями
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
            private val imageView: ImageView = itemView.findViewById(R.id.photoImageView)
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
                        val bitmap = BitmapFactory.decodeStream(stream)
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageView.setImageResource(R.drawable.error_image)
                }
            }
        }
    }

    companion object {
        fun newInstance(item: Item): DetailFragment {
            val fragment = DetailFragment()
            val args = Bundle().apply {
                putSerializable("item", item)
            }
            fragment.arguments = args
            return fragment
        }
    }
}