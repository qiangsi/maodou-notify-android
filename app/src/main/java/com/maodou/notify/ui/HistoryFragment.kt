package com.maodou.notify.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.data.CryptoHelper
import com.maodou.notify.data.Result
import com.maodou.notify.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NotificationAdapter
    private var all: List<com.maodou.notify.data.NotificationEntity> = emptyList()
    private var keyword = ""
    private var filter = FILTER_ALL
    private var searchJob: Job? = null

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_KEPT = 1
        private const val FILTER_FILTERED = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = NotificationAdapter { item ->
            startActivity(Intent(requireContext(), NotificationDetailActivity::class.java).putExtra("id", item.id))
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                keyword = s?.toString()?.trim() ?: ""
                debounceApply()
            }
        })

        binding.chipFilter.setOnCheckedChangeListener { group, checkedId ->
            filter = when (checkedId) {
                R.id.chip_kept -> FILTER_KEPT
                R.id.chip_filtered -> FILTER_FILTERED
                else -> FILTER_ALL
            }
            applyList()
        }

        val app = requireActivity().application as MaodouApp
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.notificationDao().observeAll().collectLatest { list ->
                all = list
                applyList()
            }
        }
    }

    private fun debounceApply() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            applyList()
        }
    }

    private fun applyList() {
        var list = all
        if (filter == FILTER_KEPT) list = list.filter { it.result == Result.KEPT }
        if (filter == FILTER_FILTERED) list = list.filter { it.result != Result.KEPT }
        if (keyword.isNotEmpty()) {
            val kw = keyword.lowercase()
            list = list.filter { item ->
                item.appName.lowercase().contains(kw) ||
                CryptoHelper.decrypt(item.titleEnc).lowercase().contains(kw) ||
                CryptoHelper.decrypt(item.textEnc).lowercase().contains(kw) ||
                item.packageName.lowercase().contains(kw)
            }
        }
        adapter.submitList(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
