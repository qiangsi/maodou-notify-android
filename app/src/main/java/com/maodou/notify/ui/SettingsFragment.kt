package com.maodou.notify.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.data.CryptoHelper
import com.maodou.notify.databinding.FragmentSettingsBinding
import com.maodou.notify.util.isListenerEnabled
import com.maodou.notify.util.openListenerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var app: MaodouApp

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { writeCsv(it) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        app = requireActivity().application as MaodouApp
        val prefs = app.prefs

        fun refreshPermission() {
            binding.tvPermission.text = if (isListenerEnabled(requireContext()))
                getString(R.string.settings_permission_status_on)
            else getString(R.string.settings_permission_status_off)
        }
        refreshPermission()
        binding.cardPermission.setOnClickListener { openListenerSettings(requireContext()) }

        binding.switchAi.isChecked = prefs.aiEnabled
        binding.switchAi.setOnCheckedChangeListener { _, checked -> prefs.aiEnabled = checked }
        binding.seekThreshold.progress = prefs.aiThreshold
        binding.tvThreshold.text = getString(R.string.settings_ai_threshold_value, prefs.aiThreshold)
        binding.seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                binding.tvThreshold.text = getString(R.string.settings_ai_threshold_value, p)
                if (fromUser) prefs.aiThreshold = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.switchUndo.isChecked = prefs.undoEnabled
        binding.switchUndo.setOnCheckedChangeListener { _, c -> prefs.undoEnabled = c }
        binding.switchSmartmute.isChecked = prefs.smartMuteEnabled
        binding.switchSmartmute.setOnCheckedChangeListener { _, c -> prefs.smartMuteEnabled = c }
        binding.switchCodecopy.isChecked = prefs.codeCopyEnabled
        binding.switchCodecopy.setOnCheckedChangeListener { _, c -> prefs.codeCopyEnabled = c }

        binding.btnExport.setOnClickListener {
            exportLauncher.launch("毛豆通知_${System.currentTimeMillis()}.csv")
        }

        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.settings_clear_confirm)
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        app.database.notificationDao().clearAll()
                        app.database.aiFeedbackDao().clearAll()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), R.string.settings_cleared, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun writeCsv(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val list = app.database.notificationDao().recent(20000)
                val out = requireContext().contentResolver.openOutputStream(uri) ?: return@launch
                OutputStreamWriter(out, Charsets.UTF_8).use { w ->
                    w.write("\uFEFF") // UTF-8 BOM，Excel 中文不乱码
                    w.write("时间,应用,包名,标题,正文,处理结果,原因,骚扰概率\n")
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    list.forEach { e ->
                        fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
                        w.write(listOf(
                            sdf.format(java.util.Date(e.timestamp)),
                            esc(e.appName), esc(e.packageName),
                            esc(CryptoHelper.decrypt(e.titleEnc)),
                            esc(CryptoHelper.decrypt(e.textEnc)),
                            e.result, esc(e.filterReason ?: ""), e.score.toString()
                        ).joinToString(","))
                        w.write("\n")
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.export_done, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            binding.tvPermission.text = if (isListenerEnabled(requireContext()))
                getString(R.string.settings_permission_status_on)
            else getString(R.string.settings_permission_status_off)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
