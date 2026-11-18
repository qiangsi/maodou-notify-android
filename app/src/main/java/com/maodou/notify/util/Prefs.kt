package com.maodou.notify.util

import android.content.Context
import android.content.SharedPreferences

/** 应用设置。仅存开关与阈值，不含任何通知内容。 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("maodou_prefs", Context.MODE_PRIVATE)

    var aiEnabled: Boolean
        get() = sp.getBoolean(KEY_AI_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    var aiThreshold: Int
        get() = sp.getInt(KEY_AI_THRESHOLD, 80)
        set(value) = sp.edit().putInt(KEY_AI_THRESHOLD, value).apply()

    var undoEnabled: Boolean
        get() = sp.getBoolean(KEY_UNDO, true)
        set(value) = sp.edit().putBoolean(KEY_UNDO, value).apply()

    var smartMuteEnabled: Boolean
        get() = sp.getBoolean(KEY_SMART_MUTE, false)
        set(value) = sp.edit().putBoolean(KEY_SMART_MUTE, value).apply()

    var codeCopyEnabled: Boolean
        get() = sp.getBoolean(KEY_CODE_COPY, true)
        set(value) = sp.edit().putBoolean(KEY_CODE_COPY, value).apply()

    var guideShown: Boolean
        get() = sp.getBoolean(KEY_GUIDE_SHOWN, false)
        set(value) = sp.edit().putBoolean(KEY_GUIDE_SHOWN, value).apply()

    /** 排除 App 包名，逗号分隔 */
    var excludedApps: String
        get() = sp.getString(KEY_EXCLUDED, "") ?: ""
        set(value) = sp.edit().putString(KEY_EXCLUDED, value).apply()

    fun isExcluded(pkg: String): Boolean =
        excludedApps.split(",").any { it.isNotBlank() && it == pkg }

    /** 排除语义：true=不记录也不处理；false=记录但不做任何过滤（默认不记录） */
    var excludeNoRecord: Boolean
        get() = sp.getBoolean(KEY_EXCLUDE_MODE, true)
        set(value) = sp.edit().putBoolean(KEY_EXCLUDE_MODE, value).apply()

    // —— FR-H1 历史滚动清理 ——
    /** 保留窗口（天），0=不限 */
    var retainDays: Int
        get() = sp.getInt(KEY_RETAIN_DAYS, 30)
        set(value) = sp.edit().putInt(KEY_RETAIN_DAYS, value).apply()

    /** 记录条数上限，超出按最旧优先清理 */
    var maxRecords: Int
        get() = sp.getInt(KEY_MAX_RECORDS, 50000)
        set(value) = sp.edit().putInt(KEY_MAX_RECORDS, value).apply()

    // —— FR-E2 智能静音可配 ——
    var muteThreshold: Int
        get() = sp.getInt(KEY_MUTE_THRESHOLD, 5)
        set(value) = sp.edit().putInt(KEY_MUTE_THRESHOLD, value).apply()

    var muteWindowSec: Int
        get() = sp.getInt(KEY_MUTE_WINDOW, 60)
        set(value) = sp.edit().putInt(KEY_MUTE_WINDOW, value).apply()

    var muteDurationSec: Int
        get() = sp.getInt(KEY_MUTE_DURATION, 30)
        set(value) = sp.edit().putInt(KEY_MUTE_DURATION, value).apply()

    // —— FR-E5 应用锁 ——
    var appLockEnabled: Boolean
        get() = sp.getBoolean(KEY_APP_LOCK, false)
        set(value) = sp.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** 应用锁有效期（分钟），0=每次都验证 */
    var appLockTimeoutMin: Int
        get() = sp.getInt(KEY_APP_LOCK_TIMEOUT, 1)
        set(value) = sp.edit().putInt(KEY_APP_LOCK_TIMEOUT, value).apply()

    var lastUnlockAt: Long
        get() = sp.getLong(KEY_LAST_UNLOCK, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_UNLOCK, value).apply()

    // —— FR-A3 离线学习调度 ——
    var aiLearnOnlyCharging: Boolean
        get() = sp.getBoolean(KEY_AI_LEARN_CHARGING, true)
        set(value) = sp.edit().putBoolean(KEY_AI_LEARN_CHARGING, value).apply()

    var lastLearnAt: Long
        get() = sp.getLong(KEY_LAST_LEARN, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_LEARN, value).apply()

    /** 首次启动时间，用于漏记提示的时段起点 */
    var firstRunAt: Long
        get() = sp.getLong(KEY_FIRST_RUN, 0L)
        set(value) = sp.edit().putLong(KEY_FIRST_RUN, value).apply()

    /** 监听服务断开时刻（用于漏记提示） */
    var listenerDisconnectedAt: Long
        get() = sp.getLong(KEY_LISTENER_DISCONNECTED, 0L)
        set(value) = sp.edit().putLong(KEY_LISTENER_DISCONNECTED, value).apply()

    /** 待提示的漏记起始时间，0=无 */
    var pendingMissedSince: Long
        get() = sp.getLong(KEY_PENDING_MISSED, 0L)
        set(value) = sp.edit().putLong(KEY_PENDING_MISSED, value).apply()

    companion object {
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_THRESHOLD = "ai_threshold"
        private const val KEY_UNDO = "undo_enabled"
        private const val KEY_SMART_MUTE = "smart_mute"
        private const val KEY_CODE_COPY = "code_copy"
        private const val KEY_GUIDE_SHOWN = "guide_shown"
        private const val KEY_EXCLUDED = "excluded_apps"
        private const val KEY_EXCLUDE_MODE = "exclude_no_record"
        private const val KEY_RETAIN_DAYS = "retain_days"
        private const val KEY_MAX_RECORDS = "max_records"
        private const val KEY_MUTE_THRESHOLD = "mute_threshold"
        private const val KEY_MUTE_WINDOW = "mute_window_sec"
        private const val KEY_MUTE_DURATION = "mute_duration_sec"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout_min"
        private const val KEY_LAST_UNLOCK = "last_unlock_at"
        private const val KEY_AI_LEARN_CHARGING = "ai_learn_charging"
        private const val KEY_LAST_LEARN = "last_learn_at"
        private const val KEY_FIRST_RUN = "first_run_at"
        private const val KEY_LISTENER_DISCONNECTED = "listener_disconnected_at"
        private const val KEY_PENDING_MISSED = "pending_missed_since"
    }
}
