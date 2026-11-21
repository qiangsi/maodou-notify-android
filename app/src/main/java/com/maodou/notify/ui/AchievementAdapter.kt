package com.maodou.notify.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maodou.notify.R
import com.maodou.notify.achievement.AchievementStatus
import com.maodou.notify.achievement.AchievementView
import com.maodou.notify.databinding.ItemAchievementBinding

class AchievementAdapter(
    private val onClick: (AchievementView) -> Unit
) : ListAdapter<AchievementView, AchievementAdapter.VH>(Diff) {

    inner class VH(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.achievementIcon.text = item.name.take(1)
        b.achievementName.text = item.name
        b.achievementDesc.text = item.description
        b.achievementBar.progress = (item.ratio * 100).toInt()
        b.achievementProgress.text = holder.itemView.context.getString(
            R.string.achievement_progress_text, item.current, item.target
        )
        b.achievementStatus.text = holder.itemView.context.getString(
            if (item.isUnlocked) R.string.achievement_filtered else R.string.achievement_locked
        )
        b.achievementStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                holder.itemView.context,
                if (item.status == AchievementStatus.UNLOCKED) R.color.primary else R.color.text_tertiary
            )
        )
        b.root.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<AchievementView>() {
        override fun areItemsTheSame(oldItem: AchievementView, newItem: AchievementView) =
            oldItem.defId == newItem.defId

        override fun areContentsTheSame(oldItem: AchievementView, newItem: AchievementView) =
            oldItem == newItem
    }
}
