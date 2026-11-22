package com.maodou.notify.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.listener.NotificationMonitorService
import com.maodou.notify.util.DiagnosticProbe
import com.maodou.notify.util.ThemeHelper
import com.maodou.notify.util.hasPostNotificationPermission
import com.maodou.notify.util.openListenerSettings

/**
 * v1.4.1 通知诊断页。
 * 只读取状态 + 发送一条自测通知，不修改任何过滤配置、不影响既有监听链路。
 */
class DiagnosticActivity : AppCompatActivity() {

    private lateinit var tvReport: TextView
    private lateinit var btnWatch: MaterialButton
    private lateinit var btnPerf: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private val app: MaodouApp get() = application as MaodouApp

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.themeStyle(app.prefs.themeColor))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostic)

        tvReport = findViewById(R.id.tv_report)
        btnWatch = findViewById(R.id.btn_watch)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_run_probe).setOnClickListener { runSelfProbe() }
        btnWatch.setOnClickListener { toggleSession() }
        findViewById<View>(R.id.btn_force_rebind).setOnClickListener {
            NotificationMonitorService.forceRebind(this)
            Toast.makeText(this, R.string.diag_force_rebind_done, Toast.LENGTH_LONG).show()
            refresh()
        }
        findViewById<View>(R.id.btn_refresh).setOnClickListener { refresh() }
        findViewById<View>(R.id.btn_copy).setOnClickListener { copyReport() }
        findViewById<View>(R.id.btn_logcat).setOnClickListener { showLogcat() }
        findViewById<View>(R.id.btn_open_permission).setOnClickListener { openListenerSettings(this) }
        btnPerf = findViewById(R.id.btn_perf_trace)
        btnPerf.setOnClickListener {
            app.prefs.perfTrace = !app.prefs.perfTrace
            refresh()
            Toast.makeText(
                this,
                if (app.prefs.perfTrace) R.string.diag_perf_on else R.string.diag_perf_off,
                Toast.LENGTH_SHORT
            ).show()
        }

        refresh()
    }

    /** 端到端自测：发出带 token 的通知，8 秒后读取是否被监听服务捕获 */
    private fun runSelfProbe() {
        if (!hasPostNotificationPermission(this)) {
            Toast.makeText(this, R.string.diag_need_post_permission, Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002
                )
            }
            return
        }
        val token = DiagnosticProbe.startSelfProbe(this)
        Toast.makeText(this, getString(R.string.diag_probe_sent, token), Toast.LENGTH_LONG).show()

        checkRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            DiagnosticProbe.clearProbeNotification(this)
            refresh()
            val state = DiagnosticProbe.probeState(this)
            Toast.makeText(
                this,
                if (state == DiagnosticProbe.STATE_CAPTURED) R.string.diag_probe_ok
                else R.string.diag_probe_timeout,
                Toast.LENGTH_LONG
            ).show()
        }
        checkRunnable = runnable
        handler.postDelayed(runnable, 8000L)
    }

    /** 会话观察：统计任意来源通知，验证真实通知能否进入回调 */
    private fun toggleSession() {
        val active = !app.prefs.probeSessionActive
        DiagnosticProbe.setSession(this, active)
        Toast.makeText(
            this,
            if (active) R.string.diag_session_started else R.string.diag_session_stopped,
            Toast.LENGTH_LONG
        ).show()
        refresh()
    }

    private fun refresh() {
        tvReport.text = DiagnosticProbe.buildReport(this)
        btnWatch.text = getString(
            if (app.prefs.probeSessionActive) R.string.diag_watch_stop else R.string.diag_watch
        )
        btnPerf.text = getString(if (app.prefs.perfTrace) R.string.diag_perf_on else R.string.diag_perf_off)
    }

    private fun copyReport() {
        copyText(tvReport.text.toString())
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    private fun copyText(text: String) {
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("毛豆通知诊断报告", text))
        } catch (e: Exception) { }
    }

    private fun showLogcat() {
        val text = DiagnosticProbe.captureLogcat()
        val display = if (text.length > 6000) text.takeLast(6000) else text
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.diag_logcat_title)
            .setMessage(display)
            .setPositiveButton(R.string.diag_copy) { _, _ -> copyText(text) }
            .setNegativeButton(R.string.action_ok, null)
            .show()
    }

    override fun onDestroy() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
