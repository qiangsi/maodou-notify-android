package com.maodou.notify.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.achievement.AchievementView
import com.maodou.notify.achievement.ConditionType
import com.maodou.notify.achievement.ClaimResult
import com.maodou.notify.achievement.RewardType
import com.maodou.notify.databinding.FragmentAchievementsBinding
import kotlinx.coroutines.launch
import java.util.UUID

/** 成就列表 / 详情 / 进度查询的 UI 层，数据全部来自 AchievementRepository 的 Flow 与查询接口。 */
class AchievementsFragment : Fragment(R.layout.fragment_achievements) {

    private var _binding: FragmentAchievementsBinding? = null
    private val binding get() = _binding!!
    private lateinit var app: MaodouApp
    private lateinit var adapter: AchievementAdapter
    private var all: List<AchievementView> = emptyList()
    private var selectedCategory: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAchievementsBinding.bind(view)
        app = requireActivity().application as MaodouApp

        adapter = AchievementAdapter { showDetail(it) }
        binding.achievementList.layoutManager = LinearLayoutManager(requireContext())
        binding.achievementList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            app.achievementRepo.observeCategories()
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { renderChips(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            app.achievementRepo.observeAchievements(null)
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { list ->
                    all = list
                    binding.achievementSummary.text = getString(
                        R.string.achievement_summary, list.count { it.isUnlocked }, list.size
                    )
                    refreshList()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        reconcileOnVisible()
    }

    /** show/hide 复用模式下切回本页不触发 onResume，需在变为可见时校准成就 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) reconcileOnVisible()
    }

    private fun reconcileOnVisible() {
        // 页面可见时按真实数据校准一次，兜住事件漏投与数据清理
        app.appScope.launch {
            try { app.achievementRepo.reconcile() } catch (e: Exception) { }
        }
    }

    private fun renderChips(categories: List<com.maodou.notify.data.AchievementCategoryEntity>) {
        binding.achievementCategories.removeAllViews()
        val allChip = makeChip(getString(R.string.achievement_all), selectedCategory == null) {
            selectedCategory = null
            refreshList()
        }
        binding.achievementCategories.addView(allChip)
        categories.forEach { c ->
            binding.achievementCategories.addView(makeChip(c.name, selectedCategory == c.id) {
                selectedCategory = c.id
                refreshList()
            })
        }
    }

    private fun makeChip(label: String, checked: Boolean, onClick: () -> Unit): Chip {
        val chip = Chip(requireContext())
        chip.id = View.generateViewId()
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun refreshList() {
        adapter.submitList(all.filter { selectedCategory == null || it.categoryId == selectedCategory })
    }

    private fun showDetail(item: AchievementView) {
        val condition = conditionText(item)
        val rewardText = if (item.reward.type == RewardType.NONE) {
            getString(R.string.achievement_detail_no_reward)
        } else {
            getString(R.string.achievement_detail_reward, rewardLabel(item))
        }
        val message = buildString {
            append(item.description).append("\n\n")
            append(getString(R.string.achievement_detail_condition, condition)).append("\n")
            append(getString(R.string.achievement_progress_text, item.current, item.target)).append("\n")
            append(rewardText)
            if (item.rewardClaimedAt != null) append("\n").append(getString(R.string.achievement_claim_again))
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)

        if (item.isUnlocked && item.reward.type != RewardType.NONE && item.rewardClaimedAt == null) {
            builder.setNeutralButton(R.string.achievement_claim) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = app.achievementRepo.claimReward(item.defId, UUID.randomUUID().toString())
                    val text = when (result) {
                        ClaimResult.CLAIMED -> R.string.achievement_claim_ok
                        ClaimResult.ALREADY_CLAIMED -> R.string.achievement_claim_again
                        ClaimResult.LOCKED -> R.string.achievement_claim_locked
                        else -> R.string.achievement_claim_locked
                    }
                    Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.show()
    }

    private fun conditionText(item: AchievementView): String = when (item.condition.type) {
        ConditionType.ANY_NOTIFICATION -> "记录任意一条通知"
        ConditionType.FILTERED -> "过滤通知"
        ConditionType.CATEGORY -> "类型：${item.condition.category ?: "-"}"
        ConditionType.PACKAGE -> "应用：${item.condition.packageName ?: "-"}"
        ConditionType.LEARNED -> "标注学习样本"
        ConditionType.RULE_CREATED -> "创建过滤规则"
        ConditionType.PRO_ACTIVATED -> "激活 Pro"
        ConditionType.APP_OPENED -> "分自然日打开应用"
        ConditionType.ACHIEVEMENT_UNLOCKED -> "解锁其它成就"
    }

    private fun rewardLabel(item: AchievementView): String = when (item.reward.type) {
        RewardType.NONE -> "-"
        RewardType.TITLE -> getString(R.string.achievement_reward_title) + " · " + item.reward.label
        RewardType.THEME -> getString(R.string.achievement_reward_theme) + " · " + item.reward.label
        RewardType.PRO_DAYS -> getString(R.string.achievement_reward_prodays) + " · " + item.reward.amount
    }

    override fun onDestroyView() {
        binding.achievementList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
