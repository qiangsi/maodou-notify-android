package com.maodou.notify.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.maodou.notify.MaodouApp
import java.lang.ref.WeakReference

/**
 * FR-E5 应用锁的全局守卫。
 *
 * 之前只在 MainActivity 的 onResume 检查一次，而且打开开关时把 lastUnlockAt
 * 设为当前时间，导致有效期内切换完全没反应；详情页/编辑页、切后台和锁屏也没有拦截。
 * 现在改为：应用退到后台或屏幕关闭立即上锁，任何 Activity 回到前台都统一验证。
 */
object AppLockGuard {

    @Volatile
    var locked = false

    private var resumedCount = 0
    private var current: WeakReference<FragmentActivity>? = null
    private var prompting = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 由 MaodouApp 在 Application.onCreate 注册 */
    fun install(context: Context) {
        locked = MaodouApp.instance.prefs.appLockEnabled
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) lockIfEnabled()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity) current = WeakReference(activity)
        resumedCount++
        // ActivityLifecycleCallbacks 触发时生命周期状态可能还没切到 RESUMED，
        // 延后一帧再判断，避免冷启动时漏掉验证。
        mainHandler.post { maybePrompt() }
    }

    fun onActivityPaused(@Suppress("UNUSED_PARAMETER") activity: Activity) {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
    }

    fun onActivityStopped() {
        // 所有页面都停下来才算真正退到后台；页面间跳转时 resumedCount 仍为 1
        if (resumedCount == 0) lockIfEnabled()
    }

    fun lockIfEnabled() {
        try {
            if (MaodouApp.instance.prefs.appLockEnabled) locked = true
        } catch (e: Exception) {
            // Application 尚未初始化完成，忽略
        }
    }

    /** 设置页点击「立即锁定」 */
    fun lockNow() {
        if (!MaodouApp.instance.prefs.appLockEnabled) return
        locked = true
        mainHandler.post { maybePrompt() }
    }

    /** 验证成功或关闭应用锁时调用 */
    fun unlock() {
        locked = false
        prompting = false
    }

    private fun maybePrompt() {
        val activity = current?.get() ?: return
        if (prompting || !locked) return
        val prefs = MaodouApp.instance.prefs
        if (!prefs.appLockEnabled) {
            locked = false
            return
        }
        // 双保险：进程重启后 locked 标志可能还没来得及置位，用解锁时间兜底
        val timeoutMs = prefs.appLockTimeoutMin.coerceIn(0, 240) * 60_000L
        val expired = System.currentTimeMillis() - prefs.lastUnlockAt > timeoutMs
        if (!locked && !expired) return
        locked = true
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        prompting = true
        AppLockHelper.prompt(activity, prefs,
            onSuccess = {
                prompting = false
                unlock()
            },
            onFail = {
                prompting = false
                // 不 finish 主页，否则任务栈会被销毁；退回后台即可，回来会再次要求验证
                try {
                    activity.moveTaskToBack(true)
                } catch (e: Exception) {
                    activity.finish()
                }
            }
        )
    }
}
