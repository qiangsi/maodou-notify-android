package com.maodou.notify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.R
import com.maodou.notify.MaodouApp
import com.maodou.notify.databinding.ActivityMainBinding
import com.maodou.notify.util.AppLockHelper
import com.maodou.notify.util.ThemeHelper
import com.maodou.notify.util.hasPostNotificationPermission
import com.maodou.notify.util.isListenerEnabled
import com.maodou.notify.util.openListenerSettings

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.themeStyle((application as MaodouApp).prefs.themeColor))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_history -> switchFragment(HistoryFragment())
                R.id.nav_rules -> switchFragment(RulesFragment())
                R.id.nav_stats -> switchFragment(StatsFragment())
                R.id.nav_settings -> switchFragment(SettingsFragment())
            }
            true
        }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_history
        }

        checkPermissions()
        checkAppLock()
    }

    /** 应用锁（FR-E5）：回到前台且超过有效期时验证 */
    private fun checkAppLock() {
        val app = application as MaodouApp
        if (AppLockHelper.shouldPrompt(app.prefs)) {
            AppLockHelper.prompt(this, app.prefs,
                onSuccess = { /* 验证通过，正常使用 */ },
                onFail = { finish() } // 验证失败/取消则不进入应用
            )
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkPermissions() {
        if (!isListenerEnabled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.guide_title)
                .setMessage(R.string.guide_desc)
                .setPositiveButton(R.string.guide_button) { _, _ -> openListenerSettings(this) }
                .setNegativeButton(R.string.guide_later, null)
                .show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission(this)) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // FR-P4：运行中检测通知使用权被收回
        if (!isListenerEnabled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_permission)
                .setMessage(R.string.settings_permission_lost)
                .setPositiveButton(R.string.guide_button) { _, _ -> openListenerSettings(this) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        // FR-P4 / FR-H1 边界：监听中断后的漏记透明提示
        val prefs = (application as MaodouApp).prefs
        val missed = prefs.pendingMissedSince
        if (missed > 0) {
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(missed))
            MaterialAlertDialogBuilder(this)
                .setTitle("可能漏记通知")
                .setMessage(getString(R.string.settings_missed_warning, time))
                .setPositiveButton(R.string.action_ok, null)
                .show()
            prefs.pendingMissedSince = 0L
        }
        checkAppLock()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
