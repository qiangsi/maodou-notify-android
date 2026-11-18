package com.maodou.notify.listener

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maodou.notify.MaodouApp
import com.maodou.notify.R

/** 前台保活服务：降低被系统杀后台的概率（PRD 非功能需求）。 */
class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, MaodouApp.CH_KEEP_ALIVE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_service_label))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(i)
        }
    }
}
