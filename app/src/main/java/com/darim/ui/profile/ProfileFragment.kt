// ui/profile/ProfileFragment.kt
package com.darim.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.darim.R
import com.darim.databinding.FragmentProfileBinding
import com.darim.domain.model.Review
import com.darim.domain.model.User
import com.darim.ui.MainActivity
import com.darim.ui.myitems.MyBookingsAdapter
import com.darim.ui.profile.LoginFragment
import com.darim.ui.myitems.MyItemsFragment
import com.darim.ui.utils.SessionManager
import com.darim.ui.profile.ProfileViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Проверяем, залогинен ли пользователь
        if (!SessionManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        // Получаем текущего пользователя из сессии
        currentUser = SessionManager.getCurrentUser()

        setupToolbar()
        setupListeners()
        setupObservers()

        // Загружаем данные профиля текущего пользователя
        currentUser?.id?.let { userId ->
            viewModel.loadUserProfile(userId)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Профиль"
        binding.toolbar.setNavigationIcon(null)
        binding.toolbar.inflateMenu(R.menu.profile_menu)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    showSettingsDialog()
                    true
                }
                R.id.action_help -> {
                    showHelpDialog()
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }



    private fun setupListeners() {
        binding.buttonEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.avatarImage.setOnClickListener {
            showChangeAvatarDialog()
        }

        binding.cardMyItems.setOnClickListener {
            navigateToMyItems()
        }

        binding.cardMyBookings.setOnClickListener {
            navigateToMyBookings()
        }

        binding.cardMyStats.setOnClickListener {
            showStatsDialog()
        }

        binding.menuMyItems.setOnClickListener {
            navigateToMyItems()
        }

        binding.menuMyBookings.setOnClickListener {
            navigateToMyBookings()
        }

        binding.menuFavorites.setOnClickListener {
            showComingSoon("Избранное")
        }

        binding.menuLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun setupObservers() {
        // Наблюдаем за данными пользователя
        viewModel.user.observe(viewLifecycleOwner) { user ->
            user?.let {
                displayUserInfo(it)
                currentUser = it
                // Обновляем сессию, если данные изменились
                SessionManager.saveCurrentUser(it)
            }
        }

        // Наблюдаем за статистикой
        viewModel.userStats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                displayUserStats(it)
            }
        }

        // Наблюдаем за отзывами
        /*viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewsAdapter.submitList(reviews)
            updateReviewsCount(reviews.size)
        }*/

        // Наблюдаем за состоянием загрузки
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Наблюдаем за ошибками
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun displayUserInfo(user: User) {
        binding.userName.text = user.name
        binding.userEmail.text = generateEmailFromName(user.name)
        binding.userPhone.text = user.phone

        // Рейтинг
        binding.ratingBar.rating = user.rating
        binding.ratingText.text = String.format("%.1f ★ (%d отзывов)", user.rating, user.reviews.size)

        // Аватар (заглушка)
        binding.avatarImage.setImageResource(R.drawable.ic_default_avatar)

        // Базовая статистика
        binding.statsGiven.text = user.itemsGiven.toString()
        binding.statsTaken.text = user.itemsTaken.toString()
        binding.statsRating.text = String.format("%.1f", user.rating)
    }

    private fun displayUserStats(stats: com.darim.domain.usecase.user.GetUserProfileUseCase.UserStats) {
        // Обновляем расширенную статистику
        binding.statsGiven.text = stats.itemsGiven.toString()
        binding.statsTaken.text = stats.itemsTaken.toString()
        binding.statsRating.text = String.format("%.1f", stats.rating)
    }

    private fun updateReviewsCount(count: Int) {
        binding.tabLayout.getTabAt(1)?.text = "Отзывы ($count)"
    }

    private fun generateEmailFromName(name: String): String {
        val emailName = name.lowercase().replace(" ", ".")
        return "$emailName@example.com"
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val editName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editName)
        val editPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editPhone)

        // Заполняем текущими данными
        editName.setText(currentUser?.name)
        editPhone.setText(currentUser?.phone)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editName.text.toString()
                val newPhone = editPhone.text.toString()

                if (newName.isNotBlank() && newPhone.isNotBlank()) {
                    updateProfile(newName, newPhone)
                } else {
                    Toast.makeText(requireContext(), "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateProfile(newName: String, newPhone: String) {
        currentUser?.let { user ->
            val updatedUser = user.copy(
                name = newName,
                phone = newPhone
            )
            viewModel.updateProfile(updatedUser)
        }
    }

    private fun showChangeAvatarDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи", "Удалить фото")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Изменить фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(requireContext(), "Открыть камеру", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(requireContext(), "Открыть галерею", Toast.LENGTH_SHORT).show()
                    2 -> {
                        binding.avatarImage.setImageResource(R.drawable.ic_default_avatar)
                        Toast.makeText(requireContext(), "Фото удалено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Настройки")
            .setMessage("Здесь будут настройки приложения")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Помощь")
            .setMessage("Если у вас возникли вопросы, напишите нам:\nsupport@darim.ru")
            .setPositiveButton("Написать") { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@darim.ru")
                }
                startActivity(intent)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("О приложении")
            .setMessage("Darim v1.0.0\n\nПриложение для безвозмездной передачи вещей\n\nРазработано с ❤️")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performLogout() {
        // Очищаем сессию
        SessionManager.clearSession()

        // Показываем сообщение
        Toast.makeText(requireContext(), "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()

        // Перенаправляем на экран логина
        navigateToLogin()
    }

    private fun showStatsDialog() {
        val stats = viewModel.userStats.value
        val user = currentUser

        val message = buildString {
            appendLine("📊 Статистика профиля")
            appendLine()
            appendLine("👤 Имя: ${user?.name ?: "Неизвестно"}")
            appendLine("⭐ Рейтинг: ${user?.rating ?: 0f} ★")
            appendLine("📝 Отзывов: ${user?.reviews?.size ?: 0}")
            appendLine()
            appendLine("📦 Отдано вещей: ${stats?.itemsGiven ?: 0}")
            appendLine("📥 Получено вещей: ${stats?.itemsTaken ?: 0}")
            appendLine("👁 Всего просмотров: ${stats?.totalViews ?: 0}")
            appendLine()
            appendLine("✅ Активных объявлений: ${stats?.activeItems ?: 0}")
            appendLine("📅 На платформе с ${stats?.memberSince ?: "недавно"}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Моя статистика")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showReviewDetails(review: Review) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Отзыв")
            .setMessage("""
                ⭐ Оценка: ${review.rating}/5
                
                📝 Комментарий:
                ${review.comment}
                
                📅 Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(review.date))}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showComingSoon(feature: String) {
        Toast.makeText(requireContext(), "$feature — скоро появится!", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMyItems() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MyItemsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToMyBookings() {
        val fragment = MyItemsFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MyBookingsAdapter())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}