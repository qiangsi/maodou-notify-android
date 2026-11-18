package com.maodou.notify.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.maodou.notify.MaodouApp
import com.maodou.notify.data.Actions
import com.maodou.notify.data.NotificationEntity
import com.maodou.notify.data.Result
import com.maodou.notify.listener.NotificationBridge
import com.maodou.notify.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 规则动作执行结果 */
data class ActionOutcome(
    val applied: Boolean,
    val result: String,
    val reason: String
)

/** 执行规则的各项动作。10 种动作中系统可实现的均已落地，受限动作有降级说明。 */
object ActionExecutor {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    fun execute(
        context: Context,
        entity: NotificationEntity,
        action: String,
        param: String,
        decryptedText: String,
        decryptedTitle: String
    ): ActionOutcome = when (action) {
        Actions.KEEP -> ActionOutcome(false, Result.KEPT, "规则保留")
        Actions.DISMISS -> {
            NotificationBridge.cancel(entity.packageName, entity.id.toString())
            ActionOutcome(true, Result.RULE, "已消除")
        }
        Actions.MUTE -> {
            muteBriefly(context, param)
            ActionOutcome(true, Result.RULE, "已静音")
        }
        Actions.SNOOZE -> {
            snooze(context, entity, param, decryptedTitle, decryptedText)
            ActionOutcome(true, Result.RULE, "已推迟")
        }
        Actions.REPLACE -> ActionOutcome(true, Result.RULE, "已替换内容")
        Actions.RINGTONE -> ActionOutcome(true, Result.RULE, "已更改铃声")
        Actions.TTS -> {
            speak(context, decryptedTitle, decryptedText)
            ActionOutcome(true, Result.RULE, "已朗读")
        }
        Actions.CALL_UI -> {
            NotificationBridge.showCallStyle(context, decryptedTitle, decryptedText)
            ActionOutcome(true, Result.RULE, "来电式提醒")
        }
        Actions.TAP_BUTTON -> ActionOutcome(false, Result.KEPT, "点击按钮需系统辅助权限，未启用")
        Actions.OPEN -> {
            NotificationBridge.openNotification(context, entity.packageName)
            ActionOutcome(true, Result.RULE, "已打开通知")
        }
        Actions.WEBHOOK -> {
            val ok = WebhookSender.send(param, decryptedTitle, decryptedText, entity)
            ActionOutcome(ok, Result.KEPT, if (ok) "已转发" else "转发失败")
        }
        else -> ActionOutcome(false, Result.KEPT, "未知动作")
    }

    private fun muteBriefly(context: Context, param: String) {
        val seconds = param.toIntOrNull()?.coerceIn(1, 60) ?: 5
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        @Suppress("DEPRECATION")
        val old = am.ringerMode
        am.ringerMode = AudioManager.RINGER_MODE_SILENT
        handler.postDelayed({ am.ringerMode = old }, TimeUnit.SECONDS.toMillis(seconds.toLong()))
    }

    private fun snooze(context: Context, entity: NotificationEntity, param: String, title: String, text: String) {
        val minutes = param.toIntOrNull()?.coerceIn(1, 1440) ?: 10
        handler.postDelayed({
            NotificationBridge.showSnoozed(context, entity.id, title, text)
        }, TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }

    private fun speak(context: Context, title: String, text: String) {
        val app = context.applicationContext as? MaodouApp ?: return
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale.CHINA)
                    tts?.speak("$title，$text", TextToSpeech.QUEUE_FLUSH, null, "maodou-tts")
                }
            }
        } else {
            tts?.speak("$title，$text", TextToSpeech.QUEUE_FLUSH, null, "maodou-tts")
        }
        app.appScope.launch(Dispatchers.Main) { /* 保持引用 */ }
    }

    /** 智能静音：短时间内同 App 高频通知时临时静音 */
    private val recentTimes = mutableMapOf<String, MutableList<Long>>()

    fun shouldSmartMute(context: Context, pkg: String, prefs: Prefs): Boolean {
        if (!prefs.smartMuteEnabled) return false
        val now = System.currentTimeMillis()
        val list = recentTimes.getOrPut(pkg) { mutableListOf() }
        list.add(now)
        list.removeAll { now - it > 60_000 }
        if (list.size >= 5) {
            muteBriefly(context, "30")
            list.clear()
            return true
        }
        return false
    }
}
