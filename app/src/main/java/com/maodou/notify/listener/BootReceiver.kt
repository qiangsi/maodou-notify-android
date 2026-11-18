package com.maodou.notify.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maodou.notify.util.isListenerEnabled

/** 开机/升级后恢复前台服务；通知监听权限仍需在系统设置中开启。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (isListenerEnabled(context)) {
            KeepAliveService.start(context.applicationContext)
        }
    }
}
