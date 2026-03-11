package com.darim.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.core.widget.NestedScrollView
import com.darim.R
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DetailFragment : Fragment() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var imageView: ImageView
    private lateinit var titleText: TextView
    private lateinit var sellerAvatar: ImageView
    private lateinit var sellerName: TextView
    private lateinit var sellerPhone: TextView
    private lateinit var descriptionText: TextView
    private lateinit var categoryText: TextView
    private lateinit var locationText: TextView
    private lateinit var distanceText: TextView
    private lateinit var bookButton: MaterialButton
    private lateinit var favoriteButton: FloatingActionButton

    private var currentItem: Item? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupToolbar(view)
        setupListeners()
        loadItemData()
    }

    private fun initViews(view: View) {
        scrollView = view.findViewById(R.id.scrollView)
        imageView = view.findViewById(R.id.detailImageView)
        titleText = view.findViewById(R.id.detailTitle)
        sellerAvatar = view.findViewById(R.id.sellerAvatar)
        sellerName = view.findViewById(R.id.sellerName)
        sellerPhone = view.findViewById(R.id.sellerPhone)
        descriptionText = view.findViewById(R.id.detailDescription)
        categoryText = view.findViewById(R.id.detailCategory)
        locationText = view.findViewById(R.id.detailLocation)
        distanceText = view.findViewById(R.id.detailDistance)
        bookButton = view.findViewById(R.id.bookButton)
        favoriteButton = view.findViewById(R.id.favoriteButton)
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupListeners() {
        bookButton.setOnClickListener {
            currentItem?.let { item ->
                if (item.status == ItemStatus.AVAILABLE) {
                    showBookingConfirmationDialog(item)
                } else {
                    Toast.makeText(requireContext(), "Эта вещь уже недоступна", Toast.LENGTH_SHORT).show()
                }
            }
        }

        favoriteButton.setOnClickListener {
            Toast.makeText(requireContext(), "Добавлено в избранное", Toast.LENGTH_SHORT).show()
            favoriteButton.setImageResource(android.R.drawable.btn_star_big_on)
            favoriteButton.isEnabled = false
        }

        sellerAvatar.setOnClickListener {
            Toast.makeText(requireContext(), "Профиль продавца", Toast.LENGTH_SHORT).show()
        }

        sellerPhone.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Phone", sellerPhone.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBookingConfirmationDialog(item: Item) {
        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение бронирования")
            .setMessage("Вы уверены, что хотите забрать эту вещь?\n\nПосле подтверждения контактные данные продавца будут вам показаны.")
            .setPositiveButton("Да, забрать") { _, _ ->
                confirmBooking(item)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmBooking(item: Item) {
        // Меняем статус вещи
        currentItem = item.copy(status = ItemStatus.BOOKED)

        // Отправляем событие об изменении статуса
        val bundle = Bundle().apply {
            putString("itemId", item.id)
            putSerializable("newStatus", ItemStatus.BOOKED)
        }
        parentFragmentManager.setFragmentResult("itemStatusChanged", bundle)

        // Обновляем UI
        bookButton.isEnabled = false
        bookButton.text = "Забронировано"

        // Показываем диалог с контактами (телефон берем из item)
        showContactDialog(item)
    }

    private fun showContactDialog(item: Item) {
        val phoneNumber = item.phone  // ← телефон из самого item!

        AlertDialog.Builder(requireContext())
            .setTitle("Вещь забронирована! 🎉")
            .setMessage("Продавец: ${item.ownerId}\nТелефон: $phoneNumber\n\nСвяжитесь с продавцом для договоренности о встрече.")
            .setPositiveButton("Позвонить") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            }
            .setNeutralButton("Скопировать телефон") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Phone", phoneNumber)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun loadItemData() {
        // Получаем вещь из аргументов (передаем весь Item)
        val item = arguments?.getSerializable("item") as? Item

        if (item != null) {
            displayItem(item)
        } else {
            Toast.makeText(requireContext(), "Ошибка загрузки объявления", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayItem(item: Item) {
        currentItem = item

        titleText.text = item.title
        descriptionText.text = item.description
        categoryText.text = "Категория: ${item.category}"
        locationText.text = item.location.address
        sellerName.text = "Продавец"  // Можно заменить на имя из UserRepository
        sellerPhone.text = item.phone  // ← телефон из item!

        // Рассчитываем расстояние
        distanceText.text = "📍 1.2 км от вас"

        // Заглушка для фото
        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        sellerAvatar.setImageResource(android.R.drawable.sym_def_app_icon)

        // Обновляем кнопку в зависимости от статуса
        if (item.status != ItemStatus.AVAILABLE) {
            bookButton.isEnabled = false
            bookButton.text = "Уже забрали"
        } else {
            bookButton.isEnabled = true
            bookButton.text = "Заберу"
        }
    }

    companion object {
        fun newInstance(item: Item): DetailFragment {
            val fragment = DetailFragment()
            val args = Bundle().apply {
                putSerializable("item", item)  // ← передаем всю вещь
            }
            fragment.arguments = args
            return fragment
        }
    }
}