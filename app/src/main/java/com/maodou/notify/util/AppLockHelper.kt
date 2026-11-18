package com.maodou.notify.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.maodou.notify.R

/** FR-E5：指纹/面容应用锁。带有效期缓存，避免每次切后台都重新验证。 */
object AppLockHelper {

    fun isDeviceCapable(context: Context): Boolean {
        return try {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    /** 是否需要验证：开关开启 且 距上次解锁超过有效期 */
    fun shouldPrompt(prefs: Prefs): Boolean {
        if (!prefs.appLockEnabled) return false
        val timeoutMs = prefs.appLockTimeoutMin.coerceIn(0, 240) * 60_000L
        return System.currentTimeMillis() - prefs.lastUnlockAt > timeoutMs
    }

    fun prompt(
        activity: FragmentActivity,
        prefs: Prefs,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prefs.lastUnlockAt = System.currentTimeMillis()
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // 取消类错误也放行到 fail（关闭页面），锁定类错误保持拒绝
                if (errorCode in setOf(ERROR_USER_CANCELED, ERROR_NEGATIVE_BUTTON)) onFail()
                else if (errorCode == ERROR_LOCKOUT || errorCode == ERROR_LOCKOUT_PERMANENT) onFail()
                else onFail()
            }
            override fun onAuthenticationFailed() { /* 单次失败不关闭，继续重试 */ }
        }
        try {
            BiometricPrompt(activity, executor, callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.app_lock_title))
                    .setSubtitle(activity.getString(R.string.app_lock_subtitle))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
            )
        } catch (e: Exception) {
            onFail()
        }
    }
}
