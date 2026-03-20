package com.darim.ui.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.darim.R
import com.darim.databinding.FragmentRegisterBinding
import com.darim.ui.MainActivity
import com.darim.ui.profile.RegisterViewModel

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        (requireActivity() as MainActivity).getViewModelFactory()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        setupTextWatchers()
    }

    private fun setupListeners() {
        binding.buttonRegister.setOnClickListener {
            val name = binding.editName.text.toString()
            val phone = binding.editPhone.text.toString()
            val password = binding.editPassword.text.toString()
            val confirmPassword = binding.editConfirmPassword.text.toString()

            if (validateInputs(name, phone, password, confirmPassword)) {
                viewModel.register(name, phone, password)
            }
        }

        binding.buttonBackToLogin.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is RegisterViewModel.RegisterResult.Success -> {
                    Toast.makeText(requireContext(), "Регистрация успешна! Теперь войдите.", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
                is RegisterViewModel.RegisterResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    binding.buttonRegister.isEnabled = true
                }
                is RegisterViewModel.RegisterResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.buttonRegister.isEnabled = false
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.buttonRegister.isEnabled = true
                }
            }
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonRegister.isEnabled = validateInputsForButton()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        binding.editName.addTextChangedListener(textWatcher)
        binding.editPhone.addTextChangedListener(textWatcher)
        binding.editPassword.addTextChangedListener(textWatcher)
        binding.editConfirmPassword.addTextChangedListener(textWatcher)
    }

    private fun validateInputs(name: String, phone: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (name.isBlank()) {
            binding.editName.error = "Введите имя"
            isValid = false
        } else if (name.length < 2) {
            binding.editName.error = "Имя слишком короткое"
            isValid = false
        }

        if (phone.isBlank()) {
            binding.editPhone.error = "Введите номер телефона"
            isValid = false
        } else if (phone.length < 10) {
            binding.editPhone.error = "Некорректный номер"
            isValid = false
        }

        if (password.isBlank()) {
            binding.editPassword.error = "Введите пароль"
            isValid = false
        } else if (password.length < 4) {
            binding.editPassword.error = "Пароль должен быть минимум 4 символа"
            isValid = false
        }

        if (confirmPassword != password) {
            binding.editConfirmPassword.error = "Пароли не совпадают"
            isValid = false
        }

        return isValid
    }

    private fun validateInputsForButton(): Boolean {
        val name = binding.editName.text.toString()
        val phone = binding.editPhone.text.toString()
        val password = binding.editPassword.text.toString()
        val confirmPassword = binding.editConfirmPassword.text.toString()

        return name.isNotBlank() && phone.isNotBlank() && password.isNotBlank() &&
                confirmPassword.isNotBlank() && password == confirmPassword &&
                phone.length >= 10 && password.length >= 4
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}