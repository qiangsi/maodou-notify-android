package com.maodou.notify.util

/**
 * 应用锁「何时需要验证」的纯策略，v1.4.0。
 *
 * 职责边界：只根据 [Prefs] 里记录的离开时刻与用户配置的离开时长，
 * 判断重新进入时是否要弹出验证。不持有 Activity、不弹窗、不依赖生命周期，
 * 因此可被 AppLockGuard 复用，也可单独单元测试。
 *
 * 语义：
 * - 离开应用（退后台 / 锁屏 / 主动锁定）时记录离开时刻；
 * - 重新进入时，若离开时长 >= 阈值则验证，未达阈值不打扰（如快速切换 App）；
 * - 阈值 0 表示每次进入都验证（旧行为）；
 * - 验证成功后清除离开记录，避免同一次离开被反复验证。
 */
object AppLockPolicy {

    /** 退到后台或锁屏时调用，标记一次「待验证的离开」。 */
    fun markBackground(prefs: Prefs, now: Long = System.currentTimeMillis()) {
        if (prefs.appLockEnabled) {
            prefs.lastBackgroundAt = now
        }
    }

    /**
     * 重新进入时是否需要验证。
     * 返回 false 的两种情况：应用锁未开启；或没有待验证的离开记录（本次会话从未离开）。
     */
    fun needsVerification(prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.appLockEnabled) return false
        val leftAt = prefs.lastBackgroundAt
        if (leftAt <= 0L) return false
        val awayMs = (now - leftAt).coerceAtLeast(0L)
        return awayMs >= prefs.appLockAwayMs
    }

    /** 验证成功：清除离开记录并刷新解锁时刻。 */
    fun markVerified(prefs: Prefs, now: Long = System.currentTimeMillis()) {
        prefs.lastBackgroundAt = 0L
        prefs.lastUnlockAt = now
    }

    /**
     * 「立即锁定」：把离开时刻回拨一个阈值，使 needsVerification 立即成立。
     * 不新增字段即可表达「强制下次进入验证」，与离开时长配置解耦。
     */
    fun forceLock(prefs: Prefs, now: Long = System.currentTimeMillis()) {
        prefs.lastBackgroundAt = now - prefs.appLockAwayMs.coerceAtLeast(0L)
    }

    /** 关闭应用锁 / 重置：清除待验证状态。 */
    fun clear(prefs: Prefs) {
        prefs.lastBackgroundAt = 0L
    }
}
