// ui/transfer/TransferFragment.kt
package com.darim.ui.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.darim.R
import com.darim.databinding.FragmentTransferBinding
import com.darim.domain.model.TransferStatus
import com.darim.ui.MainActivity
import com.darim.ui.utils.DateTimeHelper
import com.darim.ui.utils.SessionManager
import com.darim.ui.transfer.TransferViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!
    private val TAG = "TransferFragment"

    private val viewModel: TransferViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private var transferId: String? = null
    private var currentUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUserId = SessionManager.getCurrentUserId()
        transferId = arguments?.getString("transferId")

        if (transferId == null) {
            Toast.makeText(requireContext(), "Ошибка загрузки встречи", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setupToolbar()
        setupListeners()
        setupObservers()

        viewModel.loadTransfer(transferId!!)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupListeners() {
        binding.buttonCall.setOnClickListener {
            makePhoneCall()
        }

        binding.buttonCopyPhone.setOnClickListener {
            copyPhoneToClipboard()
        }

        binding.buttonConfirm.setOnClickListener {
            showConfirmDialog()
        }

        binding.buttonCancel.setOnClickListener {
            showCancelDialog()
        }

        binding.buttonNoShow.setOnClickListener {
            showNoShowDialog()
        }

        binding.textAddress.setOnClickListener {
            openInMaps()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transfer.observeForever{ transfer ->
                    transfer?.let {
                        displayTransferInfo(it)
                        updateUiForStatus(it.status)
                    }
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.completeResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is TransferViewModel.TransferResult.Success -> {
                    Toast.makeText(requireContext(), "Встреча подтверждена!", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
                is TransferViewModel.TransferResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.cancelResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is TransferViewModel.TransferResult.Success -> {
                    Toast.makeText(requireContext(), "Встреча отменена", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
                is TransferViewModel.TransferResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.noShowResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is TransferViewModel.TransferResult.Success -> {
                    Toast.makeText(requireContext(), "Неявка отмечена", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
                is TransferViewModel.TransferResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun displayTransferInfo(transfer: com.darim.domain.model.Transfer) {
        // Информация о вещи (в реальном приложении загружать из репозитория)
        binding.textItemTitle.text = "Вещь #${transfer.itemId.substring(0, 8)}"

        // Время встречи
        binding.textMeetingTime.text = DateTimeHelper.formatMeetingTime(transfer.scheduledTime)

        // Место встречи
        binding.textAddress.text = transfer.meetingPoint.address

        // Участники (в реальном приложении загружать имена из UserRepository)
        val isGiver = currentUserId == transfer.giverId
        if (isGiver) {
            binding.textGiverName.text = "Вы (отдаете)"
            binding.textTakerName.text = "Получатель"
            binding.textGiverPhone.text = SessionManager.getCurrentUserPhone() ?: ""
            binding.textTakerPhone.text = "Загружается..."
        } else {
            binding.textGiverName.text = "Отдающий"
            binding.textTakerName.text = "Вы (забираете)"
            binding.textGiverPhone.text = "Загружается..."
            binding.textTakerPhone.text = SessionManager.getCurrentUserPhone() ?: ""
        }
    }

    private fun updateUiForStatus(status: TransferStatus) {
        when (status) {
            TransferStatus.SCHEDULED -> {
                binding.statusBadge.text = "Встреча назначена"
                binding.statusBadge.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                binding.buttonConfirm.visibility = View.VISIBLE
                binding.buttonCancel.visibility = View.VISIBLE
                binding.buttonNoShow.visibility = View.VISIBLE
                binding.completedInfo.visibility = View.GONE
            }
            TransferStatus.COMPLETED -> {
                binding.statusBadge.text = "Встреча состоялась"
                binding.statusBadge.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                binding.buttonConfirm.visibility = View.GONE
                binding.buttonCancel.visibility = View.GONE
                binding.buttonNoShow.visibility = View.GONE
                binding.completedInfo.visibility = View.VISIBLE
                binding.textCompletedMessage.text = "Встреча успешно завершена"
            }
            TransferStatus.CANCELLED -> {
                binding.statusBadge.text = "Встреча отменена"
                binding.statusBadge.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                binding.buttonConfirm.visibility = View.GONE
                binding.buttonCancel.visibility = View.GONE
                binding.buttonNoShow.visibility = View.GONE
                binding.completedInfo.visibility = View.VISIBLE
                binding.textCompletedMessage.text = "Встреча была отменена"
            }
            TransferStatus.NO_SHOW -> {
                binding.statusBadge.text = "Не пришел"
                binding.statusBadge.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                binding.buttonConfirm.visibility = View.GONE
                binding.buttonCancel.visibility = View.GONE
                binding.buttonNoShow.visibility = View.GONE
                binding.completedInfo.visibility = View.VISIBLE
                binding.textCompletedMessage.text = "Участник не явился на встречу"
            }
        }
    }

    private fun makePhoneCall() {
        val phone = if (currentUserId == viewModel.transfer.value?.giverId) {
            viewModel.transfer.value?.takerId?.let { "Телефон получателя" } ?: ""
        } else {
            viewModel.transfer.value?.giverId?.let { "Телефон отдающего" } ?: ""
        }

        if (phone.isNotEmpty() && phone != "Загружается...") {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Телефон не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPhoneToClipboard() {
        val phone = if (currentUserId == viewModel.transfer.value?.giverId) {
            viewModel.transfer.value?.takerId?.let { "Телефон получателя" } ?: ""
        } else {
            viewModel.transfer.value?.giverId?.let { "Телефон отдающего" } ?: ""
        }

        if (phone.isNotEmpty() && phone != "Загружается...") {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("phone", phone)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Телефон не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Подтверждение встречи")
            .setMessage("Подтвердите, что встреча состоялась")
            .setPositiveButton("Да, всё прошло") { _, _ ->
                transferId?.let { id ->
                    currentUserId?.let { userId ->
                        viewModel.completeTransfer(id, userId)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCancelDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Отмена встречи")
            .setMessage("Вы уверены, что хотите отменить встречу?")
            .setPositiveButton("Отменить") { _, _ ->
                transferId?.let { id ->
                    currentUserId?.let { userId ->
                        viewModel.cancelTransfer(id, userId, "Отменено пользователем")
                    }
                }
            }
            .setNegativeButton("Оставить", null)
            .show()
    }

    private fun showNoShowDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Отметка о неявке")
            .setMessage("Отметить, что другой участник не пришел на встречу?")
            .setPositiveButton("Да, не пришел") { _, _ ->
                transferId?.let { id ->
                    currentUserId?.let { userId ->
                        viewModel.markNoShow(id, userId)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openInMaps() {
        val transfer = viewModel.transfer.value
        transfer?.let {
            val uri = Uri.parse("yandexmaps://maps.yandex.ru/?pt=${it.meetingPoint.lng},${it.meetingPoint.lat}&z=16&l=map")
            val intent = Intent(Intent.ACTION_VIEW, uri)

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                val webUri = Uri.parse("https://yandex.ru/maps/?pt=${it.meetingPoint.lng},${it.meetingPoint.lat}&z=16&l=map")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                startActivity(webIntent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(transferId: String): TransferFragment {
            val fragment = TransferFragment()
            val args = Bundle().apply {
                putString("transferId", transferId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}