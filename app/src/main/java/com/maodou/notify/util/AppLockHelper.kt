package com.maodou.notify.util

import android.content.Context
import android.widget.EditText
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.R

/** FR-E5：应用锁。支持自设 PIN（主）与系统指纹/锁屏密码（可选）。带有效期缓存。 */
object AppLockHelper {

    fun isBiometricCapable(context: Context): Boolean {
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

    /**
     * 统一验证入口：若已设置 PIN 走 PIN 对话框，并提供「改用指纹/锁屏密码」按钮；
     * 未设置 PIN 但设备支持生物识别时回退到系统验证。
     */
    fun prompt(
        activity: FragmentActivity,
        prefs: Prefs,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        if (PinHelper.hasPin(prefs)) {
            promptPin(activity, prefs, onSuccess, onFail)
        } else if (isBiometricCapable(activity)) {
            promptBiometric(activity, prefs, onSuccess, onFail)
        } else {
            // 既没 PIN 也没有生物识别能力：无法验证，按失败处理（不会误放行）
            onFail()
        }
    }

    /** PIN 验证对话框：输错可重试，点取消按失败处理 */
    private fun promptPin(
        activity: FragmentActivity,
        prefs: Prefs,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            hint = activity.getString(R.string.app_lock_pin_set_hint)
            setSingleLine(true)
        }
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad / 2, pad, pad / 2)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_lock_pin_verify_title)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.action_ok, null) // 下面覆写，校验失败不关闭
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = input.text.toString().trim()
                when {
                    pin.isEmpty() -> input.error = activity.getString(R.string.app_lock_pin_empty)
                    PinHelper.verify(prefs, pin) -> {
                        prefs.lastUnlockAt = System.currentTimeMillis()
                        dialog.dismiss()
                        onSuccess()
                    }
                    else -> {
                        input.error = activity.getString(R.string.app_lock_pin_wrong)
                        input.text?.clear()
                    }
                }
            }
            // 提供切换到系统生物识别的入口（设备支持时）
            if (isBiometricCapable(activity)) {
                dialog.setButton(android.app.AlertDialog.BUTTON_NEUTRAL,
                    activity.getString(R.string.app_lock_use_biometric)) { _, _ ->
                    dialog.dismiss()
                    promptBiometric(activity, prefs, onSuccess, onFail)
                }
            }
        }
        dialog.setOnCancelListener { onFail() }
        dialog.show()
    }

    private fun promptBiometric(
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
                onFail()
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
