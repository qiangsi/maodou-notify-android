package com.maodou.notify.util

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.R

/**
 * v1.6.0 统一 Pro 解锁面板。
 *
 * 修复：过去各门控点只弹一个 Toast（文案却写「点此解锁」），根本点不动，
 * 用户被拦下后没有任何解锁入口。现在所有付费点统一走 [gate]：
 *   - 已激活：直接执行原操作，不打扰；
 *   - 未激活：弹出真正可输入激活码的面板，激活成功后立即兑现刚才被拦下的那个操作。
 * 这个「付费瞬间立即兑现」的闭环，是转化率的关键。
 */
object ProSheet {

    /** 门控：已激活立即执行；未激活弹出解锁面板，激活成功后立即兑现。 */
    fun gate(context: Context, prefs: Prefs, featureName: String, onActive: () -> Unit) {
        if (ProHelper.isActive(prefs)) {
            onActive()
            return
        }
        show(context, prefs, featureName, onActive)
    }

    /** 展示解锁面板。已激活时改为展示状态，不重复激活。 */
    fun show(context: Context, prefs: Prefs, featureName: String = "", onSuccess: (() -> Unit)? = null) {
        if (ProHelper.isActive(prefs)) {
            showStatus(context, prefs)
            return
        }
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val primary = ThemeHelper.resolveColor(context, androidx.appcompat.R.attr.colorPrimary, R.color.primary)
        val textMain = ThemeHelper.resolveColor(context, android.R.attr.textColorPrimary, R.color.text_primary)
        val textSub = ThemeHelper.resolveColor(context, android.R.attr.textColorSecondary, R.color.text_secondary)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        if (featureName.isNotEmpty()) {
            root.addView(TextView(context).apply {
                text = context.getString(R.string.pro_sheet_hook, featureName)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(primary)
                setTypeface(typeface, Typeface.BOLD)
            })
            root.addView(TextView(context).apply {
                text = context.getString(R.string.pro_sheet_hook_sub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(textSub)
                setPadding(0, dp(2), 0, dp(10))
            })
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.pro_sheet_benefits)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(textMain)
            setLineSpacing(dp(5).toFloat(), 1f)
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.pro_sheet_price)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.pro_sheet_how)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textSub)
        })
        val input = EditText(context).apply {
            hint = context.getString(R.string.pro_dialog_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setText(prefs.proCode)
            setTextColor(textMain)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        root.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pro_sheet_title)
            .setView(root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.pro_sheet_activate, null)
            .create()
        // 覆盖默认按钮监听，校验失败时不关闭面板，用户可重新输入
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val info = ProHelper.verify(input.text.toString())
                if (info.valid) {
                    prefs.proActivated = true
                    prefs.proForever = info.forever
                    prefs.proExpireAt = info.expireAt
                    prefs.proCode = input.text.toString().trim()
                    Toast.makeText(context, R.string.pro_activate_ok, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onSuccess?.invoke()
                } else {
                    Toast.makeText(context, R.string.pro_activate_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    /** 已激活状态：展示权益与到期信息，不重复激活。 */
    fun showStatus(context: Context, prefs: Prefs) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pro_card_title)
            .setMessage(
                context.getString(R.string.pro_sheet_status, ProHelper.statusText(prefs)) +
                    "\n\n" + context.getString(R.string.pro_sheet_benefits)
            )
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }
}
