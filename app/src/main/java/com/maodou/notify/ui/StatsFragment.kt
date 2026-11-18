package com.maodou.notify.ui

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
import com.maodou.notify.databinding.FragmentStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var app: MaodouApp

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        app = requireActivity().application as MaodouApp
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::app.isInitialized) refresh()
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val total = app.database.notificationDao().countAll()
            val filtered = app.database.notificationDao().countByResult("rule") +
                           app.database.notificationDao().countByResult("ai")
            val since = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val rank = app.database.notificationDao().packageRank(since)
            val labels = rank.associate { row ->
                val label = try {
                    app.packageManager.getApplicationLabel(
                        app.packageManager.getApplicationInfo(row.packageName, 0)
                    ).toString()
                } catch (e: Exception) { row.packageName }
                row.packageName to label
            }
            withContext(Dispatchers.Main) {
                binding.tvTotal.text = total.toString()
                binding.tvFiltered.text = filtered.toString()
                binding.rankContainer.removeAllViews()
                val max = rank.firstOrNull()?.cnt ?: 1
                rank.take(10).forEach { row ->
                    val item = layoutInflater.inflate(R.layout.item_rank, binding.rankContainer, false)
                    item.findViewById<TextView>(R.id.tv_app).text = labels[row.packageName] ?: row.packageName
                    item.findViewById<TextView>(R.id.tv_count).text = "${row.cnt} 条"
                    item.findViewById<ProgressBar>(R.id.bar).apply {
                        this.max = max
                        progress = row.cnt
                    }
                    binding.rankContainer.addView(item)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
