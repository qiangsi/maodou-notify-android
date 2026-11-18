package com.maodou.notify.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maodou.notify.MaodouApp
import com.maodou.notify.R
import com.maodou.notify.data.Actions
import com.maodou.notify.data.FilterRule
import com.maodou.notify.databinding.ActivityRuleEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuleEditBinding
    private lateinit var app: MaodouApp
    private var editing: FilterRule? = null
    private var selectedPkgs = mutableListOf<String>()
    private var selectedAction = Actions.DISMISS
    private var pkgLabels = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as MaodouApp

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupActionSpinner()

        binding.btnApps.setOnClickListener { showAppPicker() }

        val id = intent.getLongExtra("id", -1L)
        if (id > 0) {
            binding.toolbar.title = getString(R.string.rule_edit_title)
            lifecycleScope.launch {
                editing = withContext(Dispatchers.IO) { app.database.ruleDao().getById(id) }
                editing?.let { fill(it) }
            }
        } else {
            binding.toolbar.title = getString(R.string.rule_new_title)
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupActionSpinner() {
        val labels = mapOf(
            Actions.KEEP to R.string.act_keep, Actions.DISMISS to R.string.act_dismiss,
            Actions.MUTE to R.string.act_mute, Actions.SNOOZE to R.string.act_snooze,
            Actions.REPLACE to R.string.act_replace, Actions.RINGTONE to R.string.act_ringtone,
            Actions.TTS to R.string.act_tts, Actions.CALL_UI to R.string.act_call_ui,
            Actions.TAP_BUTTON to R.string.act_tap_button, Actions.OPEN to R.string.act_open,
            Actions.WEBHOOK to R.string.act_webhook
        )
        val keys = Actions.ALL
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, keys.map { getString(labels[it]!!) })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAction.adapter = adapter
        binding.spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedAction = keys[position]
                binding.etParam.visibility = if (selectedAction in setOf(
                        Actions.SNOOZE, Actions.REPLACE, Actions.RINGTONE, Actions.MUTE, Actions.WEBHOOK
                    )) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showAppPicker() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString() }
        pkgLabels.clear()
        apps.forEach { pkgLabels[it.packageName] = pm.getApplicationLabel(it).toString() }
        val names = apps.map { pkgLabels[it.packageName]!! }.toTypedArray()
        val pkgs = apps.map { it.packageName }
        val checked = BooleanArray(apps.size) { idx -> selectedPkgs.contains(pkgs[idx]) }
        val selected = selectedPkgs.toMutableList()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rule_select_apps)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selected.add(pkgs[which]) else selected.remove(pkgs[which])
            }
            .setPositiveButton(R.string.action_ok) { _, _ ->
                selectedPkgs = selected.distinct().toMutableList()
                updateAppsText()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateAppsText() {
        binding.tvApps.text = if (selectedPkgs.isEmpty()) getString(R.string.rule_all_apps_hint)
        else if (selectedPkgs.size <= 3) selectedPkgs.joinToString(", ") { pkgLabels[it] ?: it }
        else "${selectedPkgs.size} 个应用"
    }

    private fun fill(rule: FilterRule) {
        binding.etName.setText(rule.name)
        selectedPkgs = rule.packageNames.split(",").filter { it.isNotBlank() }.toMutableList()
        updateAppsText()
        binding.etInclude.setText(rule.keywordInclude)
        binding.etExclude.setText(rule.keywordExclude)
        binding.etTimeStart.setText(rule.timeStartMin.takeIf { it >= 0 }?.let { fmtMin(it) } ?: "")
        binding.etTimeEnd.setText(rule.timeEndMin.takeIf { it >= 0 }?.let { fmtMin(it) } ?: "")
        binding.cbCharging.isChecked = rule.chargingOnly
        binding.cbScreenoff.isChecked = rule.screenOffOnly
        binding.etParam.setText(rule.actionParam)
        binding.cbEnabled.isChecked = rule.enabled
        val pos = Actions.ALL.indexOf(rule.action).coerceAtLeast(0)
        binding.spinnerAction.setSelection(pos)
    }

    private fun parseMin(s: String): Int {
        if (s.isBlank()) return -1
        val parts = s.split(":")
        return try {
            parts[0].toInt().coerceIn(0, 23) * 60 + parts[1].toInt().coerceIn(0, 59)
        } catch (e: Exception) { -1 }
    }

    private fun fmtMin(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入规则名称", Toast.LENGTH_SHORT).show()
            return
        }
        val rule = FilterRule(
            id = editing?.id ?: 0,
            name = name,
            enabled = binding.cbEnabled.isChecked,
            packageNames = selectedPkgs.joinToString(","),
            keywordInclude = binding.etInclude.text.toString().trim(),
            keywordExclude = binding.etExclude.text.toString().trim(),
            timeStartMin = parseMin(binding.etTimeStart.text.toString()),
            timeEndMin = parseMin(binding.etTimeEnd.text.toString()),
            chargingOnly = binding.cbCharging.isChecked,
            screenOffOnly = binding.cbScreenoff.isChecked,
            action = selectedAction,
            actionParam = binding.etParam.text.toString().trim(),
            priority = editing?.priority ?: 0
        )
        lifecycleScope.launch(Dispatchers.IO) {
            if (editing != null) app.database.ruleDao().update(rule) else app.database.ruleDao().insert(rule)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RuleEditActivity, R.string.rule_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
