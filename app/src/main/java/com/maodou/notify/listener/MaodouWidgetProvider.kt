package com.maodou.notify.listener

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.ui.MainActivity
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * FR-E4 桌面小部件：今日统计（总数/已过滤）+ 快捷入口（历史/暂停过滤）。
 * 数据定时刷新（系统 updatePeriodMillis）+ 收到通知/切换开关时主动刷新。
 */
class MaodouWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val app = context.applicationContext as? MaodouApp ?: return
                app.prefs.filterPaused = !app.prefs.filterPaused
                refreshAll(context)
            }
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.maodou.notify.widget.TOGGLE"
        const val ACTION_REFRESH = "com.maodou.notify.widget.REFRESH"

        /** 主动请求刷新所有小部件（通知落库后、设置变更后调用） */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MaodouWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, MaodouWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pi.send()
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as? MaodouApp ?: return
            val views = RemoteViews(context.packageName, R.layout.widget_maodou)

            // 今日 00:00 起点
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val since = cal.timeInMillis

            app.appScope.launch {
                val total = try { app.database.notificationDao().countSince(since) } catch (e: Exception) { 0 }
                val filtered = try { app.database.notificationDao().countFilteredSince(since) } catch (e: Exception) { 0 }
                val paused = app.prefs.filterPaused

                views.setTextViewText(R.id.widget_today, total.toString())
                views.setTextViewText(R.id.widget_filtered, filtered.toString())
                views.setTextViewText(
                    R.id.widget_btn_toggle,
                    context.getString(if (paused) R.string.widget_paused else R.string.widget_active)
                )

                // 点击整块 / 「历史」按钮 → 打开 App
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPi = PendingIntent.getActivity(
                    context, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPi)
                views.setOnClickPendingIntent(R.id.widget_btn_history, openPi)

                // 「暂停/恢复过滤」按钮 → 广播切换
                val toggle = Intent(context, MaodouWidgetProvider::class.java).apply { action = ACTION_TOGGLE }
                val togglePi = PendingIntent.getBroadcast(
                    context, 2, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_toggle, togglePi)

                try { mgr.updateAppWidget(widgetId, views) } catch (e: Exception) { }
            }
        }
    }
}
