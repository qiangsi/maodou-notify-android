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
import com.maodou.notify.databinding.ActivityMainBinding
import com.maodou.notify.util.hasPostNotificationPermission
import com.maodou.notify.util.isListenerEnabled
import com.maodou.notify.util.openListenerSettings

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
