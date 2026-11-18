package com.maodou.notify.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maodou.notify.R
import com.maodou.notify.data.CryptoHelper
import com.maodou.notify.data.NotificationEntity
import com.maodou.notify.data.Result
import com.maodou.notify.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onClick: (NotificationEntity) -> Unit
) : ListAdapter<NotificationEntity, NotificationAdapter.VH>(Diff()) {

    inner class VH(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    class Diff : DiffUtil.ItemCallback<NotificationEntity>() {
        override fun areItemsTheSame(a: NotificationEntity, b: NotificationEntity) = a.id == b.id
        override fun areContentsTheSame(a: NotificationEntity, b: NotificationEntity) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.tvApp.text = item.appName
        b.tvTime.text = fmt(item.timestamp)
        b.tvTitle.text = CryptoHelper.decrypt(item.titleEnc)
        b.tvText.text = CryptoHelper.decrypt(item.textEnc)

        val filtered = item.result != Result.KEPT
        b.tvBadge.setText(if (filtered) R.string.badge_filtered else R.string.badge_kept)
        b.tvBadge.setBackgroundResource(if (filtered) R.drawable.bg_badge_filtered else R.drawable.bg_badge_kept)
        b.tvBadge.setTextColor(b.root.context.getColor(if (filtered) R.color.badge_filtered_text else R.color.badge_kept_text))
        b.tvReason.text = item.filterReason ?: ""

        if (item.score > 0 && !filtered) {
            b.tvScore.text = "骚扰 ${item.score}%"
            b.tvScore.setTextColor(b.root.context.getColor(if (item.score >= 70) R.color.score_high else if (item.score >= 40) R.color.score_mid else R.color.score_low))
        } else {
            b.tvScore.text = ""
        }

        b.root.setOnClickListener { onClick(item) }
    }

    private fun fmt(ts: Long): String {
        val cal = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        cal.timeInMillis = ts
        return if (cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)) {
            sdf.format(Date(ts))
        } else {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }
}
