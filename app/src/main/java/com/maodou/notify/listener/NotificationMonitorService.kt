package com.maodou.notify.listener

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.maodou.notify.MaodouApp
import com.maodou.notify.data.Actions
import com.maodou.notify.data.AiFeedback
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
 * onNotificationPosted → 加密落库 → 规则匹配 → AI 打分 → 执行动作。
 */
class NotificationMonitorService : NotificationListenerService() {

    companion object {
        var instance: NotificationMonitorService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        KeepAliveService.start(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = applicationContext as? MaodouApp ?: return
        val pkg = sbn.packageName
        if (pkg == applicationContext.packageName) return // 忽略自身通知

        // 排除 App（FR-E8）
        if (app.prefs.isExcluded(pkg)) return

        val extras = sbn.notification.extras ?: Bundle()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty()) return

        val appName = try {
            applicationContext.packageManager.getApplicationLabel(
                applicationContext.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { pkg }

        app.appScope.launch {
            val (charging, screenOff) = RuleEngine.deviceState(applicationContext)
            val ctx = MatchContext(pkg, appName, title, text, sbn.postTime, charging, screenOff)

            // 1) 加密落库（FR-H1）
            val entity = NotificationEntity(
                packageName = pkg,
                appName = appName,
                titleEnc = CryptoHelper.encrypt(title),
                textEnc = CryptoHelper.encrypt(text),
                timestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis(),
                channel = sbn.notification.channelId
            )
            val id = app.database.notificationDao().insert(entity)
            val saved = entity.copy(id = id)

            // 2) 智能静音（FR-E2）
            if (ActionExecutor.shouldSmartMute(applicationContext, pkg, app.prefs)) {
                app.database.notificationDao().update(saved.copy(result = Result.RULE, filterReason = "智能静音"))
                return@launch
            }

            // 3) 规则匹配（FR-R1）
            val rules = app.database.ruleDao().enabledRules()
            val match = RuleEngine.firstMatch(rules, ctx)
            if (match != null) {
                val outcome = ActionExecutor.execute(
                    applicationContext, saved, match.rule.action, match.rule.actionParam,
                    text, title
                )
                if (outcome.applied && (outcome.result == Result.RULE)) {
                    app.database.notificationDao().update(
                        saved.copy(result = outcome.result, filterReason = outcome.reason)
                    )
                    // 5 秒撤销按钮（FR-E1）
                    if (app.prefs.undoEnabled && match.rule.action == Actions.DISMISS) {
                        NotificationBridge.showUndo(applicationContext, id, title)
                    }
                } else {
                    app.database.notificationDao().update(
                        saved.copy(filterReason = match.reason + "；" + outcome.reason)
                    )
                }
                return@launch
            }

            // 4) AI 打分（FR-A1）
            if (app.prefs.aiEnabled) {
                val feedback = app.database.aiFeedbackDao().recent(500)
                val model = SpamScorer.buildModel(feedback) { CryptoHelper.decrypt(it) }
                val score = SpamScorer.score(title, text, pkg, model)
                if (score >= app.prefs.aiThreshold) {
                    cancelNotification(sbn.key)
                    app.database.notificationDao().update(
                        saved.copy(result = Result.AI, score = score, filterReason = "AI 判定骚扰 $score%")
                    )
                } else {
                    app.database.notificationDao().update(saved.copy(score = score))
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // FR-H5：记录移除原因，历史中保留快照
        val app = applicationContext as? MaodouApp ?: return
        app.appScope.launch {
            val entity = app.database.notificationDao().recent(200)
                .firstOrNull { it.packageName == sbn.packageName && it.timestamp == sbn.postTime }
            entity?.let {
                app.database.notificationDao().setRemovedReason(it.id, "removed_by_system_or_user")
            }
        }
    }
}
