package com.maodou.notify.util

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.listener.KeepAliveService
import com.maodou.notify.listener.NotificationMonitorService
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v1.4.1 诊断探针。
 *
 * 设计目标：只回答一个问题——通知到底卡在哪一层？
 *  1) 自测：应用给自己发一条带随机 token 的通知，监听服务若在
 *     "忽略自身通知"之前捕获到 token，说明 权限+服务绑定+回调 整条链路是通的；
 *  2) 会话观察：统计任意来源通知的捕获数，验证真实通知是否进入回调；
 *  3) 静态采集：权限、连接时间、过滤配置、事件日志，一次性汇总成可复制报告。
 *
 * 该类不改动任何通知处理逻辑，只在 onNotificationPosted 的最前面"旁听"，
 * 且只写状态信息，绝不存储通知正文。
 */
object DiagnosticProbe {

    const val STATE_NONE = 0
    const val STATE_PENDING = 1
    const val STATE_CAPTURED = 2

    private const val CH_PROBE = "diagnostic_probe"
    private const val NOTIFICATION_ID = 9001
    private const val TOKEN_PREFIX = "MDPROBE-"

    // ------------------------------------------------------------------
    // 钩子：由 NotificationMonitorService 调用
    // ------------------------------------------------------------------

    /** 在 onListenerConnected 里调用，记录连接事件 */
    fun onListenerConnected(ctx: Context) {
        prefs(ctx)?.appendProbeLog("LISTENER_CONNECTED")
    }

    /** 在 onListenerDisconnected 里调用，记录断开事件 */
    fun onListenerDisconnected(ctx: Context) {
        prefs(ctx)?.appendProbeLog("LISTENER_DISCONNECTED")
    }

    /**
     * 在 onNotificationPosted 的最开头调用（必须在 pkg==self 的 return 之前），
     * 否则自测通知会被"忽略自身通知"直接丢弃，测试永远失败。
     */
    fun onNotificationSeen(ctx: Context, sbn: StatusBarNotification) {
        val p = prefs(ctx) ?: return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val combined = "$title $text"

        // 1) 自测 token 命中 → 证明监听链路通畅
        val token = p.probeToken
        if (token.isNotBlank() && p.probeState == STATE_PENDING && combined.contains(token, ignoreCase = true)) {
            p.probeState = STATE_CAPTURED
            p.appendProbeLog("SELF_PROBE_CAPTURED")
        }

        // 2) 会话观察：统计任意来源通知（只记状态，不记正文）
        if (p.probeSessionActive) {
            p.probeSessionCount = p.probeSessionCount + 1
            p.probeLastPkg = sbn.packageName
            p.probeLastAt = System.currentTimeMillis()
            p.appendProbeLog("SESSION_CAPTURED pkg=${sbn.packageName}")
        }
    }

    // ------------------------------------------------------------------
    // 自测动作：由诊断页调用
    // ------------------------------------------------------------------

    /** 发起一次端到端自测，返回本次 token（用于让用户确认通知内容） */
    fun startSelfProbe(ctx: Context): String {
        val p = prefs(ctx) ?: return ""
        val token = TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
        p.probeToken = token
        p.probeStartAt = System.currentTimeMillis()
        p.probeState = STATE_PENDING
        p.appendProbeLog("SELF_PROBE_SENT")

        val nm = ctx.getSystemService(NotificationManager::class.java)
            ?: return token.also { p.appendProbeLog("PROBE_FAILED no NotificationManager") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_PROBE, "诊断探针", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "仅用于自测通知监听链路，可关闭"
            }
            nm.createNotificationChannel(ch)
        }
        val notification = Notification.Builder(ctx, CH_PROBE)
            .setContentTitle(ctx.getString(R.string.probe_notification_title))
            .setContentText(token)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            p.appendProbeLog("PROBE_SEND_EXCEPTION ${e.javaClass.simpleName}")
        }
        return token
    }

    /** 读取自测结果：返回 0=未开始 1=等待中（超时） 2=已捕获 */
    fun probeState(ctx: Context): Int = prefs(ctx)?.probeState ?: STATE_NONE

    /** 清掉自测通知，避免长期驻留通知栏 */
    fun clearProbeNotification(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) { }
    }

    /** 开始/停止会话观察 */
    fun setSession(ctx: Context, active: Boolean) {
        val p = prefs(ctx) ?: return
        p.probeSessionActive = active
        if (active) {
            p.probeSessionStartAt = System.currentTimeMillis()
            p.probeSessionCount = 0
            p.probeLastPkg = ""
            p.probeLastAt = 0L
            p.appendProbeLog("SESSION_START")
        } else {
            p.appendProbeLog("SESSION_STOP count=${p.probeSessionCount}")
        }
    }

    // ------------------------------------------------------------------
    // 报告生成
    // ------------------------------------------------------------------

    /** 生成诊断报告全文（可直接复制发给开发者） */
    fun buildReport(ctx: Context): String {
        val p = prefs(ctx)
            ?: return "诊断失败：无法获取 MaodouApp 实例"
        val sb = StringBuilder()
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        sb.appendLine("===== 毛豆通知 · 诊断报告 =====")
        sb.appendLine("生成时间：${fmt.format(Date(now))}")
        sb.appendLine()

        // 1. 设备环境
        sb.appendLine("【1. 设备环境】")
        sb.appendLine("厂商 / 型号：${Build.MANUFACTURER} / ${Build.MODEL}")
        sb.appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val version = try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (versionCode ${pi.versionCode})"
        } catch (e: Exception) { "未知" }
        sb.appendLine("应用版本：$version")
        sb.appendLine("包名：${ctx.packageName}")
        sb.appendLine()

        // 2. 权限状态
        val listenerOn = isListenerEnabled(ctx)
        val postOn = hasPostNotificationPermission(ctx)
        val listenersValue = try {
            Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "(空)"
        } catch (e: Exception) { "(读取失败)" }
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                ctx.getSystemService(android.os.PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            } catch (e: Exception) { false }
        } else true
        sb.appendLine("【2. 权限状态】")
        sb.appendLine("★通知使用权(Notification access)：${if (listenerOn) "已授予" else "未授予 ← 最常见根因"}")
        sb.appendLine("  系统值：${listenersValue.take(300)}")
        sb.appendLine("通知发送权限(POST_NOTIFICATIONS)：${if (postOn) "已授予" else "未授予（自测通知可能发不出）"}")
        sb.appendLine("电池优化白名单：${if (batteryIgnored) "已加入" else "未加入（后台可能被限制）"}")
        sb.appendLine()

        // 3. 服务状态
        val connected = NotificationMonitorService.isConnected
        val ca = p.listenerConnectedAt
        val da = p.listenerDisconnectedAt
        sb.appendLine("【3. 服务状态】")
        sb.appendLine("监听服务实例：${if (connected) "存在" else "不存在"}")
        sb.appendLine("最近连接(onListenerConnected)：${if (ca > 0) "${fmt.format(Date(ca))}（${(now - ca) / 60000} 分钟前）" else "从未连接 ← 重点"}")
        sb.appendLine("最近断开(onListenerDisconnected)：${if (da > 0) fmt.format(Date(da)) else "无"}")
        sb.appendLine("保活前台服务：${serviceState(ctx, KeepAliveService::class.java.name)}")
        sb.appendLine()

        // 4. 过滤配置
        val enabledRules = try {
            runBlocking { (MaodouApp.instance.database.ruleDao().enabledRules()).size }
        } catch (e: Exception) { -1 }
        val historyCount = try {
            runBlocking { MaodouApp.instance.database.notificationDao().countAll() }
        } catch (e: Exception) { -1 }
        sb.appendLine("【4. 过滤配置】")
        sb.appendLine("全局暂停过滤：${if (p.filterPaused) "是（此时只记录不过滤）" else "否"}")
        sb.appendLine("AI 骚扰识别：${if (p.aiEnabled) "开启" else "关闭"}，阈值 ${p.aiThreshold}%")
        sb.appendLine("默认规则已注入：${if (p.defaultRulesSeeded) "是" else "否"}")
        sb.appendLine("已启用规则数：$enabledRules")
        sb.appendLine("历史记录数：$historyCount")
        sb.appendLine("排除名单：${p.excludedApps.ifBlank { "空" }}（${if (p.excludeNoRecord) "不记录不过滤" else "记录不过滤"}）")
        sb.appendLine()

        // 5. 探针结果
        val state = p.probeState
        val stateText = when (state) {
            STATE_CAPTURED -> "已捕获 ✅（监听链路通）"
            STATE_PENDING -> "等待中 / 超时未捕获 ❌"
            else -> "未开始"
        }
        sb.appendLine("【5. 自探针结果】")
        sb.appendLine("端到端自测：$stateText")
        if (p.probeStartAt > 0) sb.appendLine("自测发起：${fmt.format(Date(p.probeStartAt))}")
        if (state == STATE_CAPTURED && p.probeToken.isNotBlank()) {
            sb.appendLine("本次标记：${p.probeToken}")
        }
        sb.appendLine("会话观察：${if (p.probeSessionActive) "进行中" else "未开始"}，已捕获 ${p.probeSessionCount} 条")
        if (p.probeLastPkg.isNotBlank()) {
            sb.appendLine("最后捕获来源：${p.probeLastPkg} @ ${fmt.format(Date(p.probeLastAt))}")
        }
        sb.appendLine()

        // 6. 事件日志
        sb.appendLine("【6. 事件日志（最近 20 条）】")
        val log = p.probeLog
        if (log.isBlank()) {
            sb.appendLine("（无任何事件，说明监听服务从未回调过）")
        } else {
            log.lines().takeLast(20).forEach { sb.appendLine(it) }
        }
        sb.appendLine()
        sb.appendLine(conclusion(listenerOn, ca, state, p.probeSessionCount, p.filterPaused, enabledRules))
        return sb.toString()
    }

    /** 抓取与应用相关的 logcat 行（失败时给出 adb 命令兜底） */
    fun captureLogcat(): String {
        return try {
            val pid = android.os.Process.myPid()
            var raw = exec("logcat -d -t 500 --pid=$pid")
            if (raw.isBlank()) raw = exec("logcat -d -t 500")
            if (raw.isBlank()) {
                return "（应用内读不到 logcat。请在电脑上执行：\nadb logcat | grep -E \"Maodou|NotificationListener|SecurityException\"）"
            }
            val filtered = raw.lines().filter { line ->
                line.contains("maodou", true) ||
                    line.contains("notificationlistener", true) ||
                    line.contains("securityexception", true) ||
                    line.contains("notifymonitor", true) ||
                    line.contains("webhook", true)
            }.takeLast(80).joinToString("\n")
            filtered.ifBlank { "（logcat 中没有与本应用相关的条目）" }
        } catch (e: Exception) {
            "logcat 读取失败：${e.javaClass.simpleName} ${e.message ?: ""}\n可在电脑上执行：adb logcat | grep -E \"Maodou|NotificationListener\""
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private fun prefs(ctx: Context): Prefs? =
        (ctx.applicationContext as? MaodouApp)?.prefs

    private fun serviceState(ctx: Context, className: String): String = try {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val running = am?.getRunningServices(200)?.any { it.service.className == className } == true
        if (running) "运行中" else "未运行（看门狗会尝试恢复）"
    } catch (e: Exception) { "无法读取" }

    private fun exec(command: String): String {
        val process = Runtime.getRuntime().exec(command)
        val text = try {
            process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            try { process.destroy() } catch (e: Exception) { }
        }
        return text
    }

    private fun conclusion(
        listenerOn: Boolean,
        connectedAt: Long,
        probeState: Int,
        sessionCount: Int,
        filterPaused: Boolean,
        ruleCount: Int
    ): String {
        val sb = StringBuilder("【7. 自动诊断结论】\n")
        when {
            !listenerOn -> sb.appendLine(
                "通知使用权未授予 —— 这是最高频根因。请到 系统设置 → 通知/特殊应用权限 → 通知使用权，" +
                    "把毛豆通知打开。注意它和「通知发送权限」不是一回事：前者才允许读取别人的通知。"
            )
            connectedAt == 0L -> sb.appendLine(
                "监听服务从未真正连接过系统。权限可能刚开未生效，或厂商 ROM 拦截。" +
                    "建议：回到设置页点「重新连接监听」并重新打开应用；国产机型放开自启动/后台运行。"
            )
            probeState == STATE_CAPTURED || sessionCount > 0 -> {
                sb.appendLine("监听链路正常：自测或真实通知已进入回调（捕获 $sessionCount 条）。如果仍觉得「没拦截」，问题在过滤层：")
                if (filterPaused) sb.appendLine("  · 你打开了「暂停过滤」——此时只记录不拦截，请关闭。")
                if (ruleCount <= 0) sb.appendLine("  · 没有任何启用的规则，且 AI 分数可能未达阈值，导致不动作。")
                sb.appendLine("  · 确认规则关键词与通知内容匹配；确认通知不在排除名单。")
                sb.appendLine("  · 注意：替换内容/改铃声等动作受系统限制本就不会生效，只有「消除通知(DISMISS)」能真正拦掉。")
            }
            else -> sb.appendLine(
                "监听服务曾连接，但自测与会话观察都没捕获到通知。可能是服务被解绑、厂商限制了回调，" +
                    "或通知使用权被临时收回。建议：点「重新连接监听」→ 完全退出应用重开 → 再跑一次自测；" +
                    "国产机型逐项放开自启动与电池限制。"
            )
        }
        return sb.toString()
    }
}
