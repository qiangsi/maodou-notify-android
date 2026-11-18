package com.maodou.notify.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maodou.notify.MaodouApp
import com.maodou.notify.databinding.FragmentRulesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RuleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as MaodouApp
        adapter = RuleAdapter(
            onEdit = { rule ->
                startActivity(Intent(requireContext(), RuleEditActivity::class.java).putExtra("id", rule.id))
            },
            onToggle = { rule, checked ->
                lifecycleScope.launch {
                    app.database.ruleDao().update(rule.copy(enabled = checked))
                }
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), RuleEditActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            app.database.ruleDao().observeAll().collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fragment 重建时数据由 Flow 自动刷新
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
