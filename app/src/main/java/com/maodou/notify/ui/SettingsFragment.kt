package com.maodou.notify.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.data.CryptoHelper
import com.maodou.notify.databinding.FragmentSettingsBinding
import com.maodou.notify.engine.ActionExecutor
import com.maodou.notify.listener.MaodouWidgetProvider
import com.maodou.notify.listener.NotificationMonitorService
import com.maodou.notify.util.AppLockGuard
import com.maodou.notify.util.AppLockHelper
import com.maodou.notify.util.PinHelper
import com.maodou.notify.util.ThemeHelper
import com.maodou.notify.util.isListenerEnabled
import com.maodou.notify.util.openListenerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var app: MaodouApp
    private var lockSwitchBusy = false

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

        // 运行状态自检（v1.3.2）：权限/监听/过滤三态一眼可见
        fun refreshStatus() {
            val permissionOn = isListenerEnabled(requireContext())
            binding.tvStatusPermission.text = getString(
                if (permissionOn) R.string.settings_status_on else R.string.settings_status_off
            )
            val connected = permissionOn && NotificationMonitorService.isConnected
            binding.tvStatusListener.text = getString(
                if (connected) R.string.settings_status_on else R.string.settings_status_off
            )
            binding.tvStatusFilter.text = getString(
                if (prefs.filterPaused) R.string.settings_status_filter_off
                else R.string.settings_status_filter_on
            )
        }
        refreshStatus()
        binding.btnRebind.setOnClickListener {
            NotificationMonitorService.rebindIfNeeded(requireContext())
            Toast.makeText(requireContext(), R.string.settings_status_rebind_done, Toast.LENGTH_SHORT).show()
            binding.root.postDelayed({ if (_binding != null) refreshStatus() }, 800)
        }

        // 外观：深浅模式 + 配色（FR-E6），切换即时生效并记忆
        fun refreshThemeLabels() {
            binding.tvThemeModeValue.text = getString(R.string.theme_mode_label, ThemeHelper.MODE_NAMES[prefs.themeMode.coerceIn(0..2)])
            binding.tvThemeColorValue.text = getString(R.string.theme_color_label, ThemeHelper.COLOR_NAMES[prefs.themeColor.coerceIn(0..3)])
        }
        refreshThemeLabels()
        binding.tvThemeMode.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_appearance)
                .setSingleChoiceItems(ThemeHelper.MODE_NAMES, prefs.themeMode.coerceIn(0..2)) { d, which ->
                    prefs.themeMode = which
                    ThemeHelper.applyNightMode(which)
                    MaodouWidgetProvider.refreshAll(requireContext())
                    d.dismiss()
                    requireActivity().recreate()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        binding.tvThemeColor.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.theme_color_label, ThemeHelper.COLOR_NAMES[prefs.themeColor.coerceIn(0..3)]))
                .setSingleChoiceItems(ThemeHelper.COLOR_NAMES, prefs.themeColor.coerceIn(0..3)) { d, which ->
                    prefs.themeColor = which
                    MaodouWidgetProvider.refreshAll(requireContext())
                    d.dismiss()
                    requireActivity().recreate()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        // 暂停过滤（FR-E4）：仅记录不过滤
        binding.switchFilterPaused.isChecked = prefs.filterPaused
        binding.switchFilterPaused.setOnCheckedChangeListener { _, c ->
            prefs.filterPaused = c
            MaodouWidgetProvider.refreshAll(requireContext())
            refreshStatus()
        }

        // AI 开关与阈值
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

        // 通知增强
        binding.switchUndo.isChecked = prefs.undoEnabled
        binding.switchUndo.setOnCheckedChangeListener { _, c -> prefs.undoEnabled = c }
        binding.switchSmartmute.isChecked = prefs.smartMuteEnabled
        binding.switchSmartmute.setOnCheckedChangeListener { _, c -> prefs.smartMuteEnabled = c }
        binding.switchCodecopy.isChecked = prefs.codeCopyEnabled
        binding.switchCodecopy.setOnCheckedChangeListener { _, c -> prefs.codeCopyEnabled = c }

        // 智能静音可配（FR-E2）
        fun refreshMuteCfg() {
            binding.tvMuteCfg.text = getString(R.string.settings_mute_threshold, prefs.muteThreshold, prefs.muteWindowSec)
            binding.tvMuteDuration.text = getString(R.string.settings_mute_duration, prefs.muteDurationSec)
        }
        refreshMuteCfg()
        binding.tvMuteCfg.setOnClickListener { showNumberPicker("触发条数", prefs.muteThreshold, 2..100) { prefs.muteThreshold = it; refreshMuteCfg() } }
        binding.tvMuteDuration.setOnClickListener { showNumberPicker("静音时长(秒)", prefs.muteDurationSec, 5..600) { prefs.muteDurationSec = it; refreshMuteCfg() } }
        binding.btnUnmute.setOnClickListener {
            ActionExecutor.unmute(requireContext())
            Toast.makeText(requireContext(), R.string.settings_unmuted, Toast.LENGTH_SHORT).show()
        }

        binding.btnExport.setOnClickListener {
            // FR-H6：导出前让用户选择筛选范围
            val ranges = arrayOf("全部", "近 7 天", "近 30 天")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("导出范围")
                .setItems(ranges) { _, which ->
                    exportDays = when (which) { 1 -> 7; 2 -> 30; else -> 0 }
                    exportLauncher.launch("毛豆通知_${System.currentTimeMillis()}.csv")
                }
                .show()
        }

        // 排除 App（FR-E8）
        binding.btnExcludeApps.setOnClickListener { showExcludeApps() }
        binding.switchExcludeMode.isChecked = prefs.excludeNoRecord
        binding.switchExcludeMode.setOnCheckedChangeListener { _, c -> prefs.excludeNoRecord = c }

        // 重置模型（FR-A4 / P3）
        binding.btnResetModel.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.settings_reset_model_confirm)
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        app.database.aiFeedbackDao().clearAll()
                        app.database.notificationDao().resetAllScores()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), R.string.settings_reset_model_done, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        // 应用锁（FR-E5）：开启需先自设密码，关闭立即生效，退后台/锁屏后重新验证
        binding.switchAppLock.isChecked = prefs.appLockEnabled
        binding.switchAppLock.setOnCheckedChangeListener { _, enable ->
            if (lockSwitchBusy) return@setOnCheckedChangeListener
            if (enable) {
                if (PinHelper.hasPin(prefs)) {
                    // 已有密码：验证旧密码后重新启用
                    AppLockHelper.prompt(requireActivity(), prefs,
                        onSuccess = { enableAppLock(prefs) },
                        onFail = {
                            setLockSwitch(false)
                            Toast.makeText(requireContext(), R.string.app_lock_canceled, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    // 首次开启：先设置一个 4-6 位数字密码
                    showPinSetup(
                        onDone = {
                            PinHelper.setPin(prefs, it)
                            enableAppLock(prefs)
                            Toast.makeText(requireContext(), R.string.app_lock_pin_set_done, Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { setLockSwitch(false) }
                    )
                }
            } else {
                prefs.appLockEnabled = false
                AppLockGuard.unlock()
                Toast.makeText(requireContext(), R.string.app_lock_off, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLockNow.setOnClickListener {
            if (!prefs.appLockEnabled) {
                Toast.makeText(requireContext(), R.string.app_lock_need_enable, Toast.LENGTH_SHORT).show()
            } else {
                AppLockGuard.lockNow()
                Toast.makeText(requireContext(), R.string.app_lock_locked, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangePin.setOnClickListener {
            if (!PinHelper.hasPin(prefs)) {
                Toast.makeText(requireContext(), R.string.app_lock_need_enable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 修改密码前先验证当前密码
            AppLockHelper.prompt(requireActivity(), prefs,
                onSuccess = {
                    showPinSetup(
                        onDone = {
                            PinHelper.setPin(prefs, it)
                            Toast.makeText(requireContext(), R.string.app_lock_pin_set_done, Toast.LENGTH_SHORT).show()
                        },
                        onCancel = {}
                    )
                },
                onFail = {}
            )
        }

        binding.btnBatteryOpt.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = requireContext().getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                    Toast.makeText(requireContext(), R.string.battery_whitelisted, Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${requireContext().packageName}")
                        ))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.battery_whitelist_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
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

    private var exportDays = 0

    private fun showNumberPicker(title: String, current: Int, range: IntRange, onPick: (Int) -> Unit) {
        val values = (range.first..range.last).map { it.toString() }.toTypedArray()
        val start = current - range.first
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(values, start.coerceIn(0, values.size - 1)) { dialog, which ->
                onPick(range.first + which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showExcludeApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireContext().packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { pm.getApplicationLabel(it).toString() to it.packageName }
                .sortedBy { it.first }
                .take(100) // 避免对话框过长
            withContext(Dispatchers.Main) {
                if (installed.isEmpty()) return@withContext
                val names = installed.map { it.first }.toTypedArray()
                val current = app.prefs.excludedApps.split(",").toSet()
                val checked = BooleanArray(installed.size) { i -> installed[i].second in current }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_exclude_apps)
                    .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton(R.string.action_save) { _, _ ->
                        val picked = installed.filterIndexed { i, _ -> checked[i] }.map { it.second }
                        app.prefs.excludedApps = picked.joinToString(",")
                        Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }
    }

    private fun writeCsv(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = app.database.notificationDao()
                val list = if (exportDays > 0) {
                    val from = System.currentTimeMillis() - exportDays * 86_400_000L
                    dao.queryForExport(from, System.currentTimeMillis(), "")
                } else {
                    dao.queryForExport(0L, Long.MAX_VALUE, "")
                }
                val out = requireContext().contentResolver.openOutputStream(uri) ?: return@launch
                OutputStreamWriter(out, Charsets.UTF_8).use { w ->
                    w.write("\uFEFF") // UTF-8 BOM，Excel 中文不乱码
                    w.write("时间,应用,包名,标题,正文,处理结果,原因,骚扰概率,类型\n")
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    list.forEach { e ->
                        fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
                        w.write(listOf(
                            sdf.format(Date(e.timestamp)),
                            esc(e.appName), esc(e.packageName),
                            esc(CryptoHelper.decrypt(e.titleEnc)),
                            esc(CryptoHelper.decrypt(e.textEnc)),
                            e.result, esc(e.filterReason ?: ""), e.score.toString(), esc(e.category ?: "")
                        ).joinToString(","))
                        w.write("\n")
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_done), Toast.LENGTH_SHORT).show()
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
            val permissionOn = isListenerEnabled(requireContext())
            binding.tvStatusPermission.text = getString(
                if (permissionOn) R.string.settings_status_on else R.string.settings_status_off
            )
            val connected = permissionOn && NotificationMonitorService.isConnected
            binding.tvStatusListener.text = getString(
                if (connected) R.string.settings_status_on else R.string.settings_status_off
            )
            binding.tvStatusFilter.text = getString(
                if (app.prefs.filterPaused) R.string.settings_status_filter_off
                else R.string.settings_status_filter_on
            )
        }
    }

    private fun setLockSwitch(checked: Boolean) {
        lockSwitchBusy = true
        binding.switchAppLock.isChecked = checked
        lockSwitchBusy = false
    }

    private fun enableAppLock(prefs: com.maodou.notify.util.Prefs) {
        prefs.appLockEnabled = true
        // 强制下次从后台回来时验证，而不是沿用刚才的解锁时间
        prefs.lastUnlockAt = 0L
        AppLockGuard.unlock()
        setLockSwitch(true)
        Toast.makeText(requireContext(), R.string.app_lock_on, Toast.LENGTH_SHORT).show()
    }

    /** 弹对话框设置 PIN：两次输入一致且为 4-6 位数字才回调 onDone */
    private fun showPinSetup(onDone: (String) -> Unit, onCancel: () -> Unit) {
        val ctx = requireContext()
        fun newField(hintRes: Int) = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            hint = getString(hintRes)
            setSingleLine(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val first = newField(R.string.app_lock_pin_set_hint)
        val second = newField(R.string.app_lock_pin_confirm_hint)
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(first)
            addView(second)
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.app_lock_pin_set_title)
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when {
                    !PinHelper.isValidFormat(first.text.toString()) -> first.error = getString(R.string.app_lock_pin_invalid)
                    first.text.toString() != second.text.toString() -> second.error = getString(R.string.app_lock_pin_mismatch)
                    else -> {
                        dialog.dismiss()
                        onDone(first.text.toString())
                    }
                }
            }
        }
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
