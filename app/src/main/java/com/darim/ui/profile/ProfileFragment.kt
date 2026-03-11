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
import com.darim.R
import com.darim.databinding.FragmentProfileBinding
import com.darim.ui.list.ListFragment
import com.darim.ui.myitems.MyItemsFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Временные данные пользователя
    private val user = User(
        id = "user1",
        name = "Иван Петров",
        email = "ivan.petrov@email.com",
        phone = "+7 (999) 123-45-67",
        rating = 4.8f,
        reviewsCount = 24,
        itemsGiven = 15,
        itemsTaken = 8,
        registeredDate = "Март 2023",
        avatar = null // будет использована заглушка
    )

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

        setupToolbar()
        setupListeners()
        displayUserInfo()
        setupStats()
        setupMenu()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Профиль"
        binding.toolbar.setNavigationIcon(null) // Убираем кнопку назад, так как это главный экран
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
    }

    private fun displayUserInfo() {
        binding.userName.text = user.name
        binding.userEmail.text = user.email
        binding.userPhone.text = user.phone

        // Рейтинг
        binding.ratingBar.rating = user.rating
        binding.ratingText.text = String.format("%.1f ★ (%d отзывов)", user.rating, user.reviewsCount)

        // Аватар (заглушка)
        binding.avatarImage.setImageResource(R.drawable.ic_default_avatar)
    }

    private fun setupStats() {
        binding.statsGiven.text = user.itemsGiven.toString()
        binding.statsTaken.text = user.itemsTaken.toString()
        binding.statsRating.text = String.format("%.1f", user.rating)
    }

    private fun setupMenu() {
        // Мои объявления
        binding.menuMyItems.setOnClickListener {
            navigateToMyItems()
        }

        // Мои брони
        binding.menuMyBookings.setOnClickListener {
            navigateToMyBookings()
        }

        // Избранное
        binding.menuFavorites.setOnClickListener {
            showComingSoon("Избранное")
        }

        // Настройки
        binding.menuSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Помощь
        binding.menuHelp.setOnClickListener {
            showHelpDialog()
        }

        // О приложении
        binding.menuAbout.setOnClickListener {
            showAboutDialog()
        }

        // Выйти
        binding.menuLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                Toast.makeText(requireContext(), "Профиль обновлен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showChangeAvatarDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи", "Удалить фото")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Изменить фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(requireContext(), "Открыть камеру", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(requireContext(), "Открыть галерею", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(requireContext(), "Фото удалено", Toast.LENGTH_SHORT).show()
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
            .setMessage("Darim v1.0.0\n\nПриложение для безвозмездной передачи вещей")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ ->
                Toast.makeText(requireContext(), "Выход из аккаунта", Toast.LENGTH_SHORT).show()
                // Здесь логика выхода
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showStatsDialog() {
        val stats = """
            📊 Статистика:
            
            Отдано вещей: ${user.itemsGiven}
            Получено вещей: ${user.itemsTaken}
            Рейтинг: ${user.rating} ★
            Отзывов: ${user.reviewsCount}
            
            На платформе с ${user.registeredDate}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Моя статистика")
            .setMessage(stats)
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
        // Показываем MyItemsFragment с открытой вкладкой броней
        val fragment = MyItemsFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Временная модель данных
    data class User(
        val id: String,
        val name: String,
        val email: String,
        val phone: String,
        val rating: Float,
        val reviewsCount: Int,
        val itemsGiven: Int,
        val itemsTaken: Int,
        val registeredDate: String,
        val avatar: String? = null
    )
}