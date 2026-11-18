package com.maodou.notify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.maodou.notify.data.AppDatabase
import com.maodou.notify.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaodouApp : Application() {

    /** 应用级作用域，用于后台保存通知等无需绑定 UI 生命周期的任务 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(CH_RESTORED, getString(R.string.channel_restored), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_KEEP_ALIVE, getString(R.string.channel_keep_alive), NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CH_SNOOZED, getString(R.string.channel_snoozed), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_CALL_UI, getString(R.string.channel_call_ui), NotificationManager.IMPORTANCE_HIGH)
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CH_RESTORED = "restored"
        const val CH_KEEP_ALIVE = "keep_alive"
        const val CH_SNOOZED = "snoozed"
        const val CH_CALL_UI = "call_ui"

        lateinit var instance: MaodouApp
            private set
    }
}
