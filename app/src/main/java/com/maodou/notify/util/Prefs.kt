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

    companion object {
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_THRESHOLD = "ai_threshold"
        private const val KEY_UNDO = "undo_enabled"
        private const val KEY_SMART_MUTE = "smart_mute"
        private const val KEY_CODE_COPY = "code_copy"
        private const val KEY_GUIDE_SHOWN = "guide_shown"
        private const val KEY_EXCLUDED = "excluded_apps"
    }
}
