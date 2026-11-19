package com.maodou.notify.listener

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.maodou.notify.MaodouApp
import com.maodou.notify.data.Actions
import com.maodou.notify.data.CryptoHelper
import com.maodou.notify.data.FilterRule
import com.maodou.notify.data.NotificationEntity
import com.maodou.notify.data.Result
import com.maodou.notify.engine.ActionExecutor
import com.maodou.notify.engine.MatchContext
import com.maodou.notify.engine.RuleEngine
import com.maodou.notify.engine.SpamScorer
import kotlinx.coroutines.launch

/**
 * 通知监听服务：产品技术底座。
 * onNotificationPosted → 加密落库 → 排除判定 → 规则匹配 → AI 打分 → 执行动作。
 */
class NotificationMonitorService : NotificationListenerService() {

    companion object {
        var instance: NotificationMonitorService? = null
            private set

        /** 监听服务是否真正连接到系统。用于设置页显示真实运行状态。 */
        val isConnected: Boolean get() = instance != null

        /**
         * 请求系统重新绑定监听服务。
         * 进程被杀/用户划掉最近任务后，权限仍在但服务可能未连接；主动 rebind 可恢复接管。
         */
        fun rebindIfNeeded(context: Context) {
            if (instance != null) return
            try {
                val cn = ComponentName(context.applicationContext, NotificationMonitorService::class.java)
                requestRebind(cn)
            } catch (e: Exception) { }
        }

        /** 验证码识别正则：6~8 位数字（可含字母），独立成词或紧邻「验证码/.code」上下文 */
        private val CODE_PATTERN = Regex("(?<![A-Za-z0-9])([A-Z0-9]{4,8}|\\d{4,8})(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        private val CODE_HINT = listOf("验证码", "校验码", "动态码", "code", "取件码", "取件")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        (applicationContext as? MaodouApp)?.prefs?.listenerDisconnectedAt = System.currentTimeMillis()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val app = applicationContext as? MaodouApp
        if (app != null) {
            val disc = app.prefs.listenerDisconnectedAt
            // 断开超过 1 分钟视为可能漏记（FR-P4 漏记透明提示）
            if (disc > 0 && System.currentTimeMillis() - disc > 60_000L) {
                app.prefs.pendingMissedSince = disc
            }
            app.prefs.listenerDisconnectedAt = 0L
            app.prefs.listenerConnectedAt = System.currentTimeMillis()
            if (app.prefs.firstRunAt == 0L) app.prefs.firstRunAt = System.currentTimeMillis()
        }
        KeepAliveService.start(applicationContext)
    }

    /**
     * 服务被系统解绑（进程回收/系统清理）时，尽可能立即请求重新绑定。
     * 权限仍在时 requestRebind 可恢复接管，无需用户手动往返系统设置。
     */
    override fun onListenerDisconnected() {
        (applicationContext as? MaodouApp)?.prefs?.listenerDisconnectedAt = System.currentTimeMillis()
        try {
            val cn = ComponentName(applicationContext, NotificationMonitorService::class.java)
            requestRebind(cn)
        } catch (e: Exception) { }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = applicationContext as? MaodouApp ?: return
        val pkg = sbn.packageName
        if (pkg == applicationContext.packageName) return // 忽略自身通知

        // 排除 App（FR-E8 两档语义）
        if (app.prefs.isExcluded(pkg) && app.prefs.excludeNoRecord) return

        val extras = sbn.notification.extras ?: Bundle()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        // 正文多来源兜底：普通文本 → 大文本 → 多行文本 → MessagingStyle 最后一条 → ticker
        val text = extractText(extras)
        // 标题与正文都为空时，只要通知本身有效就记录（用应用名占位），避免漏记
        if (title.isEmpty() && text.isEmpty() && sbn.notification.tickerText.isNullOrEmpty()) {
            // 完全无可读内容的通知（如纯进度条）跳过，避免污染历史
            return
        }
        val finalTitle = title.ifEmpty { sbn.notification.tickerText?.toString() ?: "" }
        val finalText = text.ifEmpty { sbn.notification.tickerText?.toString() ?: "" }

        val appName = try {
            applicationContext.packageManager.getApplicationLabel(
                applicationContext.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { pkg }

        val category = categorize(sbn, finalTitle, finalText)

        app.appScope.launch {
            // 排除第二档：记录但不过滤，直接落库后返回
            val excludedNoFilter = app.prefs.isExcluded(pkg) && !app.prefs.excludeNoRecord

            val ctx = MatchContext(
                pkg, appName, finalTitle, finalText, sbn.postTime,
                RuleEngine.deviceState(applicationContext).first,
                RuleEngine.deviceState(applicationContext).second
            )

            // 1) 加密落库（FR-H1）——加密失败拒绝存储，绝不落明文
            val entity = try {
                NotificationEntity(
                    packageName = pkg,
                    appName = appName,
                    titleEnc = CryptoHelper.encrypt(finalTitle),
                    textEnc = CryptoHelper.encrypt(finalText),
                    timestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis(),
                    channel = sbn.notification.channelId,
                    sysKey = sbn.key,
                    category = category
                )
            } catch (e: Exception) {
                return@launch // 加密失败：不落库，保护隐私
            }
            val dao = app.database.notificationDao()
            val id = dao.insert(entity)
            var saved = entity.copy(id = id)
            // FR-E4：通知落库后主动刷新桌面小部件统计
            try { MaodouWidgetProvider.refreshAll(applicationContext) } catch (e: Exception) { }

            // FR-H1 滚动清理：最旧优先
            try { performRollingCleanup(app, dao.countAll()) } catch (e: Exception) { }

            if (excludedNoFilter) return@launch // 排除档位2：记录不过滤

            // FR-E4 全局暂停过滤：仅记录，不执行任何规则/AI/静音
            if (app.prefs.filterPaused) return@launch

            // 2) 智能静音（FR-E2）
            if (ActionExecutor.shouldSmartMute(applicationContext, pkg, app.prefs)) {
                dao.update(saved.copy(result = Result.RULE, filterReason = "智能静音"))
                return@launch
            }

            // 3) 规则匹配（FR-R1）
            val rules = app.database.ruleDao().enabledRules()
            val match = RuleEngine.firstMatch(rules, ctx)
            if (match != null) {
                val outcome = ActionExecutor.execute(
                    applicationContext, saved, match.rule.action, match.rule.actionParam,
                    finalText, finalTitle
                )
                if (outcome.applied && outcome.result == Result.RULE) {
                    dao.update(saved.copy(result = outcome.result, filterReason = outcome.reason))
                    // 5 秒撤销按钮（FR-E1）：消除且开启时
                    if (app.prefs.undoEnabled && match.rule.action == Actions.DISMISS) {
                        NotificationBridge.showUndo(applicationContext, id, finalTitle)
                    }
                } else {
                    saved = saved.copy(filterReason = match.reason + "；" + outcome.reason)
                    dao.update(saved)
                }
                // 验证码复制（FR-E3）：即使命中规则也尝试附加复制入口
                maybeCodeCopy(app, category, finalTitle, finalText, saved)
                return@launch
            }

            // 4) AI 打分（FR-A1/A3）
            if (app.prefs.aiEnabled) {
                val feedback = app.database.aiFeedbackDao().recent(500)
                val model = SpamScorer.buildModel(feedback) { CryptoHelper.decrypt(it) }
                val score = SpamScorer.score(finalTitle, finalText, pkg, model)
                if (score >= app.prefs.aiThreshold) {
                    try { cancelNotification(sbn.key) } catch (e: Exception) { }
                    dao.update(saved.copy(result = Result.AI, score = score, filterReason = "AI 判定骚扰 $score%"))
                } else {
                    dao.update(saved.copy(score = score))
                }
            }

            // 5) 验证码复制（FR-E3）
            maybeCodeCopy(app, category, finalTitle, finalText, saved)

            // 6) AI 闲置/充电学习（FR-A3）：低频触发，避免前台卡顿
            maybeLearn(app)
        }
    }

    /** 滚动清理：按保留窗口与条数上限，最旧优先（FR-H1） */
    private suspend fun performRollingCleanup(app: MaodouApp, total: Int) {
        val dao = app.database.notificationDao()
        val max = app.prefs.maxRecords.coerceIn(1000, 500000)
        if (total > max) dao.deleteOldest(total - max)
        val days = app.prefs.retainDays
        if (days > 0) dao.deleteBefore(System.currentTimeMillis() - days * 86_400_000L)
    }

    /** AI 学习调度：仅在充电（且用户要求）时触发，两次至少间隔 6 小时（FR-A3） */
    private fun maybeLearn(app: MaodouApp) {
        val now = System.currentTimeMillis()
        if (now - app.prefs.lastLearnAt < 6 * 3_600_000L) return
        val charging = RuleEngine.deviceState(applicationContext).first
        if (app.prefs.aiLearnOnlyCharging && !charging) return
        app.appScope.launch {
            val feedback = app.database.aiFeedbackDao().recent(500)
            if (feedback.size >= 10) {
                SpamScorer.buildModel(feedback) { CryptoHelper.decrypt(it) }
                app.prefs.lastLearnAt = System.currentTimeMillis()
            }
        }
    }

    /** 验证码识别与复制（FR-E3）：无法给他人通知加按钮时，降级为本 App 通知内一键复制 */
    private fun maybeCodeCopy(
        app: MaodouApp, category: String?, title: String, text: String, entity: NotificationEntity
    ) {
        if (!app.prefs.codeCopyEnabled || category != "code") return
        val combined = "$title $text"
        if (!CODE_HINT.any { combined.contains(it, ignoreCase = true) }) return
        val code = CODE_PATTERN.findAll(combined)
            .map { it.value }
            .firstOrNull { it.length in 4..8 } ?: return
        NotificationBridge.showCodeCopy(applicationContext, entity.id, code)
    }

    /**
     * 正文多来源兜底。很多 IM/社交 App 把正文放在不同字段，
     * 只取 EXTRA_TEXT 会漏掉大量通知，导致「来通知但历史里看不到」。
     */
    private fun extractText(extras: Bundle): String {
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) {
            val joined = lines.joinToString(" | ") { it.toString() }
            if (joined.isNotBlank()) return joined
        }
        // MessagingStyle：取最后一条消息文本（android.messages 为 Parcelable[] Bundle）
        val msgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        if (!msgs.isNullOrEmpty()) {
            val last = msgs.lastOrNull()
            if (last is Bundle) {
                val m = last.getCharSequence("text")?.toString()
                if (!m.isNullOrBlank()) return m
            }
        }
        return ""
    }

    /** 通知类型分类：验证码 / 常驻 / 媒体 / 普通（FR-H2 类型筛选） */
    private fun categorize(sbn: StatusBarNotification, title: String, text: String): String {
        val combined = "$title $text"
        if (CODE_HINT.any { combined.contains(it, ignoreCase = true) } && CODE_PATTERN.containsMatchIn(combined)) return "code"
        if (sbn.isOngoing) return "ongoing"
        val cat = sbn.notification.category ?: ""
        if (cat == Notification.CATEGORY_TRANSPORT) return "media"
        if (cat == Notification.CATEGORY_CALL) return "call"
        return "normal"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // FR-H5：通知到达时已快照落库，移除后历史仍可查看。
        // 系统不提供可靠的「发送方撤回」判定，故统一如实标记为「已离开通知栏」，
        // 不虚构「已被撤回」结论（符合 PRD 不夸大能力边界）。
        val app = applicationContext as? MaodouApp ?: return
        app.appScope.launch {
            val dao = app.database.notificationDao()
            val entity = dao.recent(300).firstOrNull {
                it.sysKey == sbn.key || (it.packageName == sbn.packageName && it.timestamp == sbn.postTime)
            }
            entity?.let { dao.setRemovedReason(it.id, "left_notification_shade") }
        }
    }
}
