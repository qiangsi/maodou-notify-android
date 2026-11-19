package com.maodou.notify.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.data.DailyRaw
import com.maodou.notify.data.PackageCount
import com.maodou.notify.data.Result
import com.maodou.notify.databinding.FragmentStatsBinding
import com.maodou.notify.util.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var app: MaodouApp
    private var scope = SCOPE_ALL
    private var days = 7

    companion object {
        private const val SCOPE_ALL = 0
        private const val SCOPE_KEPT = 1
        private const val SCOPE_FILTERED = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        app = requireActivity().application as MaodouApp

        binding.chipScope.setOnCheckedChangeListener { _, checkedId ->
            scope = when (checkedId) {
                R.id.chip_scope_kept -> SCOPE_KEPT
                R.id.chip_scope_filtered -> SCOPE_FILTERED
                else -> SCOPE_ALL
            }
            refresh()
        }
        binding.chipWindow.setOnCheckedChangeListener { _, checkedId ->
            days = if (checkedId == R.id.chip_30d) 30 else 7
            refresh()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::app.isInitialized && _binding != null) refresh()
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = app.database.notificationDao()
            val total = dao.countAll()
            val filtered = dao.countByResult(Result.RULE) + dao.countByResult(Result.AI)
            val since = System.currentTimeMillis() - days * 86_400_000L
            val raw = dao.dailyRaw(since) // timestamp + result
            val learned = dao.countLearned()

            // 排行：按口径取对应结果的应用分布（FR-S1 分口径查看）
            val rank = when (scope) {
                SCOPE_ALL -> dao.packageRank(since)
                SCOPE_KEPT -> dao.packageRankByResult(since, Result.KEPT)
                else -> {
                    val kept = dao.packageRankByResult(since, Result.KEPT)
                    val filtered = dao.packageRankByResult(since, Result.RULE) + dao.packageRankByResult(since, Result.AI)
                    (kept + filtered).groupBy { it.packageName }
                        .map { PackageCount(it.key, it.value.sumOf { p -> p.cnt }) }
                        .sortedByDescending { it.cnt }
                }
            }
            val labels = rank.associate { row ->
                val label = try {
                    app.packageManager.getApplicationLabel(
                        app.packageManager.getApplicationInfo(row.packageName, 0)
                    ).toString()
                } catch (e: Exception) { row.packageName }
                row.packageName to label
            }

            // 日报趋势：按天聚合总数/过滤数（FR-S2）
            val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            val trend = linkedMapOf<String, IntArray>() // label -> [total, filtered]
            // 初始化最近 days 天，保证无数据日也显示
            for (i in days - 1 downTo 0) {
                cal.timeInMillis = System.currentTimeMillis()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                trend[dayFmt.format(cal.time)] = intArrayOf(0, 0)
            }
            raw.forEach { r: DailyRaw ->
                val key = dayFmt.format(Date(r.timestamp))
                val arr = trend.getOrPut(key) { intArrayOf(0, 0) }
                arr[0] += 1
                if (r.result != Result.KEPT) arr[1] += 1
            }

            withContext(Dispatchers.Main) {
                binding.tvTotal.text = total.toString()
                binding.tvFiltered.text = filtered.toString()
                binding.tvLearned.text = learned.toString()

                // 趋势条
                binding.dailyContainer.removeAllViews()
                val maxTrend = trend.values.maxOfOrNull { it[0] }?.coerceAtLeast(1) ?: 1
                trend.forEach { (day, arr) ->
                    val row = layoutInflater.inflate(R.layout.item_trend, binding.dailyContainer, false)
                    row.findViewById<TextView>(R.id.tv_day).text = day
                    row.findViewById<TextView>(R.id.tv_num).text = "${arr[0]} / ${arr[1]}"
                    row.findViewById<ProgressBar>(R.id.bar).apply {
                        max = maxTrend
                        progress = arr[0]
                        applyProgressTint(this)
                    }
                    binding.dailyContainer.addView(row)
                }

                // 排行
                binding.rankContainer.removeAllViews()
                binding.tvRankEmpty.visibility = if (rank.isEmpty()) View.VISIBLE else View.GONE
                val max = rank.firstOrNull()?.cnt ?: 1
                rank.take(10).forEach { row: PackageCount ->
                    val item = layoutInflater.inflate(R.layout.item_rank, binding.rankContainer, false)
                    item.findViewById<TextView>(R.id.tv_app).text = labels[row.packageName] ?: row.packageName
                    item.findViewById<TextView>(R.id.tv_count).text = "${row.cnt} 条"
                    item.findViewById<ProgressBar>(R.id.bar).apply {
                        this.max = max; progress = row.cnt
                        applyProgressTint(this)
                    }
                    // 点击排行直达规则创建（FR-S2）
                    item.setOnClickListener {
                        startActivity(Intent(requireContext(), RuleEditActivity::class.java).putExtra("pkg", row.packageName))
                    }
                    binding.rankContainer.addView(item)
                }
            }
        }
    }

    /** 统计图表进度条跟随当前主题配色，避免深色模式下出现系统默认灰色。 */
    private fun applyProgressTint(bar: ProgressBar) {
        val primary = ThemeHelper.resolveColor(
            requireContext(), androidx.appcompat.R.attr.colorPrimary, R.color.primary
        )
        bar.progressTintList = ColorStateList.valueOf(primary)
        bar.progressBackgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.surface_alt))
    }

    private fun scopeMatch(result: String) = when (scope) {
        SCOPE_KEPT -> result == Result.KEPT
        SCOPE_FILTERED -> result != Result.KEPT
        else -> true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
