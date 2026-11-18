package com.maodou.notify.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.ui.MainActivity

/**
 * 通知操作桥接：恢复、推迟、来电式提醒、打开通知、撤销。
 * cancel() 由 NotificationListenerService 实现。
 */
object NotificationBridge {

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun cancel(packageName: String, key: String) {
        // 实际取消在 NotificationMonitorService 中通过 cancelNotification(key) 完成
        NotificationMonitorService.instance?.let { service ->
            try { service.cancelNotification(key) } catch (e: Exception) { }
        }
    }

    /** FR-H4：把历史通知以本 App 名义重新发布到通知栏 */
    fun showRestored(context: Context, id: Long, title: String, text: String, appName: String) {
        val openPi = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java).putExtra("open_history", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, MaodouApp.CH_RESTORED)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("来源：$appName")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm(context).notify("restored_$id".hashCode(), n)
    }

    /** 5 秒撤销按钮（FR-E1） */
    fun showUndo(context: Context, id: Long, title: String) {
        val undoPi = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, UndoReceiver::class.java).putExtra("id", id).putExtra("title", title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, MaodouApp.CH_RESTORED)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.undo_notification_title))
            .setContentText(title)
            .addAction(R.drawable.ic_history, context.getString(R.string.undo_action), undoPi)
            .setTimeoutAfter(5000)
            .setAutoCancel(true)
            .build()
        nm(context).notify("undo_$id".hashCode(), n)
    }

    fun showSnoozed(context: Context, id: Long, title: String, text: String) {
        val n = NotificationCompat.Builder(context, MaodouApp.CH_SNOOZED)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm(context).notify("snoozed_$id".hashCode(), n)
    }

    /** 来电式强提醒（FR-R3-7）：全屏 + 持续响铃，尽力而为 */
    fun showCallStyle(context: Context, title: String, text: String) {
        val fullScreenPi = PendingIntent.getActivity(
            context, 99,
            Intent(context, MainActivity::class.java).putExtra("call_ui", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, MaodouApp.CH_CALL_UI)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()
        nm(context).notify("call_ui".hashCode(), n)
    }

    fun openNotification(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
    }
}
