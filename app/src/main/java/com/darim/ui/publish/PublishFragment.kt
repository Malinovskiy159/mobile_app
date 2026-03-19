// ui/publish/PublishFragment.kt
package com.darim.ui.publish

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.darim.R
import com.darim.databinding.FragmentPublishBinding
import com.darim.domain.model.Location
import com.darim.ui.MainActivity
import com.darim.ui.utils.SessionManager
import com.darim.ui.utils.UserLocationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PublishFragment : Fragment() {

    private var _binding: FragmentPublishBinding? = null
    private val binding get() = _binding!!
    private val TAG = "PublishFragment"

    // Получаем ViewModel через фабрику из MainActivity
    private val viewModel: PublishViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private val categories = listOf(
        "Техника", "Книги", "Одежда", "Мебель",
        "Детские товары", "Спорт", "Инструменты", "Освещение", "Другое"
    )

    private val selectedImageUris = mutableListOf<Uri>()
    private val selectedImageFiles = mutableListOf<File>()

    private var currentPhotoPath: String = ""
    private var photoFile: File? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.take(10 - selectedImageUris.size).forEach { uri ->
            selectedImageUris.add(uri)
            addImageToGallery(uri)
        }
        updatePhotoCounter()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                selectedImageUris.add(uri)
                selectedImageFiles.add(file)
                addImageToGallery(uri)
                updatePhotoCounter()
                photoFile = null
            }
        } else {
            Toast.makeText(requireContext(), "Фото не сделано", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val storageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }

        when {
            cameraGranted && storageGranted -> {
                showImageSourceDialog()
            }
            cameraGranted -> {
                openCamera()
            }
            else -> {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublishBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupCategorySpinner()
        setupListeners()
        setupImageGallery()
        loadUserLocation()
        observePublishResult()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupCategorySpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
    }

    private fun setupListeners() {
        binding.buttonAddPhoto.setOnClickListener {
            checkPermissionsAndShowDialog()
        }

        binding.buttonTakePhoto.setOnClickListener {
            checkPermissionsAndShowDialog()
        }

        binding.buttonPublish.setOnClickListener {
            validateAndPublish()
        }

        binding.textLocation.setOnClickListener {
            loadUserLocation()
        }
    }

    private fun setupImageGallery() {
        binding.imageGallery.removeAllViews()
        binding.imageGallery.visibility = if (selectedImageUris.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updatePhotoCounter() {
        binding.textPhotoCount.text = "${selectedImageUris.size}/10 фото"
        binding.imageGallery.visibility = if (selectedImageUris.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun addImageToGallery(uri: Uri) {
        val container = layoutInflater.inflate(R.layout.item_photo_thumbnail, null)

        val imageView = container.findViewById<ImageView>(R.id.thumbnailImage)
        val removeButton = container.findViewById<ImageView>(R.id.removeButton)

        imageView.setImageURI(uri)

        removeButton.setOnClickListener {
            val index = selectedImageUris.indexOf(uri)
            if (index >= 0) {
                selectedImageUris.removeAt(index)
                if (index < selectedImageFiles.size) {
                    val file = selectedImageFiles[index]
                    file.delete()
                    selectedImageFiles.removeAt(index)
                }
            }
            binding.imageGallery.removeView(container)
            updatePhotoCounter()
        }

        binding.imageGallery.addView(container)
    }

    private fun checkPermissionsAndShowDialog() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isEmpty()) {
            showImageSourceDialog()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Требуются разрешения")
            .setMessage("Для добавления фото необходимо разрешить доступ к камере и файлам. Перейти в настройки?")
            .setPositiveButton("Настройки") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openCamera() {
        if (selectedImageUris.size >= 10) {
            Toast.makeText(requireContext(), "Максимум 10 фото", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            photoFile = createImageFile()
            photoFile?.let { file ->
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                cameraLauncher.launch(uri)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating file", e)
            Toast.makeText(requireContext(), "Ошибка создания файла", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).also { file ->
            currentPhotoPath = file.absolutePath
        }
    }

    private fun openGallery() {
        if (selectedImageUris.size >= 10) {
            Toast.makeText(requireContext(), "Максимум 10 фото", Toast.LENGTH_SHORT).show()
            return
        }
        imagePickerLauncher.launch("image/*")
    }

    private fun loadUserLocation() {
        lifecycleScope.launch {
            val location = UserLocationManager.getLastKnownLocation()
            if (location != null) {
                binding.textLocation.text = location.address
                binding.textCoordinates.text = String.format(
                    "%.4f, %.4f",
                    location.lat,
                    location.lng
                )
            } else {
                binding.textLocation.text = "Местоположение не определено"
                binding.textCoordinates.text = ""
            }
        }
    }

    private fun validateAndPublish() {
        val title = binding.editTitle.text.toString()
        val category = binding.spinnerCategory.selectedItem?.toString() ?: ""
        val description = binding.editDescription.text.toString()
        val location = UserLocationManager.getLastKnownLocation()

        if (title.isBlank()) {
            binding.editTitle.error = "Введите название"
            return
        }

        if (category.isBlank()) {
            Toast.makeText(requireContext(), "Выберите категорию", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isBlank()) {
            binding.editDescription.error = "Введите описание"
            return
        }

        if (location == null) {
            Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(requireContext(), "Добавьте хотя бы одно фото", Toast.LENGTH_SHORT).show()
            return
        }

        // Получаем текущего пользователя из сессии
        val currentUser = SessionManager.getCurrentUser()

        /*viewModel.publishItem(
            title = title,
            category = category,
            description = description,
            location = location,
            ownerId = currentUser?.id ?: "guest",
            ownerName = currentUser?.name ?: "Гость",
            ownerPhone = currentUser?.phone ?: "+7 (999) 000-00-00",
            photoUris = selectedImageUris,
            photoFiles = selectedImageFiles
        )*/

        viewModel.publishItem(
            title = title,
            category = category,
            description = description,
            location = location,
            ownerId = "user1",
            ownerName = "Иван Петров",
            ownerPhone = "+7 (999) 123-45-67",
            photoUris = selectedImageUris,
            photoFiles = selectedImageFiles
        )
    }

    private fun observePublishResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publishResult.collect { result ->
                    when (result) {
                        is PublishViewModel.PublishUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.buttonPublish.isEnabled = false
                        }
                        is PublishViewModel.PublishUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.buttonPublish.isEnabled = true
                            Toast.makeText(requireContext(), "Объявление опубликовано!", Toast.LENGTH_LONG).show()
                            parentFragmentManager.popBackStack()
                        }
                        is PublishViewModel.PublishUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.buttonPublish.isEnabled = true
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): PublishFragment {
            return PublishFragment()
        }
    }
}