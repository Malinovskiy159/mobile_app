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
import com.darim.R
import com.darim.databinding.FragmentTransferBinding
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.ui.utils.DateTimeHelper

class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    // Временные данные
    private val transfer = Transfer(
        id = "transfer1",
        itemId = "1",
        giverId = "user1",
        takerId = "user2",
        status = TransferStatus.SCHEDULED,
        scheduledTime = System.currentTimeMillis() + 86400000, // завтра
        meetingPoint = com.darim.domain.model.Location(
            55.7558,
            37.6176,
            "м. Охотный ряд, у входа"
        )
    )

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

        setupToolbar()
        setupListeners()
        displayTransferInfo()
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
            confirmMeeting()
        }

        binding.buttonCancel.setOnClickListener {
            cancelMeeting()
        }

        binding.buttonNoShow.setOnClickListener {
            markNoShow()
        }

        binding.textAddress.setOnClickListener {
            openInMaps()
        }
    }

    private fun displayTransferInfo() {
        binding.textItemTitle.text = "Кухонный комбайн Braun" // В реальности загрузить из ItemRepository
        binding.textMeetingTime.text = DateTimeHelper.formatMeetingTime(transfer.scheduledTime)
        binding.textAddress.text = transfer.meetingPoint.address
        binding.textGiverName.text = "Иван Петров (Отдает)" // В реальности загрузить из UserRepository
        binding.textTakerName.text = "Мария Иванова (Забирает)" // В реальности загрузить из UserRepository
        binding.textGiverPhone.text = "+7 (999) 123-45-67"
        binding.textTakerPhone.text = "+7 (999) 234-56-78"

        // Обновляем UI в зависимости от статуса
        updateUiForStatus()
    }

    private fun updateUiForStatus() {
        when (transfer.status) {
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
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:+79991234567")
        }
        startActivity(intent)
    }

    private fun copyPhoneToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Phone", "+7 (999) 123-45-67")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Телефон скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun confirmMeeting() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение встречи")
            .setMessage("Подтвердите, что встреча состоялась")
            .setPositiveButton("Да, всё прошло") { _, _ ->
                Toast.makeText(requireContext(), "Встреча подтверждена", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun cancelMeeting() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Отмена встречи")
            .setMessage("Вы уверены, что хотите отменить встречу?")
            .setPositiveButton("Отменить") { _, _ ->
                Toast.makeText(requireContext(), "Встреча отменена", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Оставить", null)
            .show()
    }

    private fun markNoShow() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Отметка о неявке")
            .setMessage("Отметить, что другой участник не пришел на встречу?")
            .setPositiveButton("Да, не пришел") { _, _ ->
                Toast.makeText(requireContext(), "Неявка отмечена", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openInMaps() {
        val gmmIntentUri = Uri.parse("geo:55.7558,37.6176?q=55.7558,37.6176(Место встречи)")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(requireContext(), "Google Maps не установлен", Toast.LENGTH_SHORT).show()
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