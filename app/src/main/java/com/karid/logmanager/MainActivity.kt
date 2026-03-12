package com.karid.logmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.karid.logmanager.databinding.ActivityMainBinding
import com.karid.logmanager.model.LogType
import com.karid.logmanager.service.LogService
import com.karid.logmanager.ui.AiAnswerDialog
import com.karid.logmanager.ui.AiHistoryAdapter
import com.karid.logmanager.ui.AiPopupDialog
import com.karid.logmanager.ui.FirstLaunchDialog
import com.karid.logmanager.ui.InfoActivity
import com.karid.logmanager.ui.LogHistoryActivity
import com.karid.logmanager.ui.ReadLogsPermissionDialog
import com.karid.logmanager.ui.SettingsActivity
import com.karid.logmanager.utils.LocaleHelper
import com.karid.logmanager.utils.PrefsHelper
import com.karid.logmanager.utils.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var aiHistoryAdapter: AiHistoryAdapter

    private val stopLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateLogButtons(isRunning = false)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupLogButtons()
        setupAiButton()
        setupAiHistoryList()
        setupPermStatusCard()

        autoGrantReadLogsIfRooted()

        if (PrefsHelper.isFirstLaunch(this)) {
            FirstLaunchDialog().show(supportFragmentManager, "FirstLaunchDialog")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("from_notification", false)) {
            updateLogButtons(isRunning = false)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLogButtons(isRunning = LogService.isRunning)
        refreshAiHistory()
        updatePermStatusCard()

        val filter = IntentFilter("com.karid.logmanager.LOG_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopLogReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopLogReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(stopLogReceiver) } catch (_: Exception) {}
    }

    private fun autoGrantReadLogsIfRooted() {
        if (RootHelper.isRooted() && !RootHelper.hasReadLogsPermission(this)) {
            CoroutineScope(Dispatchers.Main).launch {
                val success = withContext(Dispatchers.IO) {
                    RootHelper.grantReadLogsViaRoot(this@MainActivity)
                }
                if (success) {
                    updatePermStatusCard()
                    Toast.makeText(this@MainActivity,
                        getString(R.string.perm_auto_granted_root),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupPermStatusCard() {
        binding.btnPermAction.setOnClickListener { showPermissionDialog() }
    }

    private fun updatePermStatusCard() {
        val hasPermission = RootHelper.hasReadLogsPermission(this)
        if (hasPermission) {
            val method = if (RootHelper.isRooted()) getString(R.string.perm_status_granted_root)
                         else getString(R.string.perm_status_granted_adb)
            binding.tvPermStatus.text = method
            binding.tvPermStatus.setTextColor(getColor(R.color.green_soft))
            binding.btnPermAction.visibility = View.GONE
        } else {
            binding.tvPermStatus.text = getString(R.string.perm_status_missing)
            binding.tvPermStatus.setTextColor(getColor(R.color.red_stop))
            binding.btnPermAction.visibility = View.VISIBLE
        }

        val disabledAlpha = 0.38f
        val enabledAlpha  = 1.0f
        if (!hasPermission && !LogService.isRunning) {
            binding.btnSoftLog.isEnabled   = false
            binding.btnNormalLog.isEnabled = false
            binding.btnDeepLog.isEnabled   = false
            binding.btnSoftLog.alpha   = disabledAlpha
            binding.btnNormalLog.alpha = disabledAlpha
            binding.btnDeepLog.alpha   = disabledAlpha
        } else if (!LogService.isRunning) {
            binding.btnSoftLog.isEnabled   = true
            binding.btnNormalLog.isEnabled = true
            binding.btnDeepLog.isEnabled   = RootHelper.isRooted()
            binding.btnSoftLog.alpha   = enabledAlpha
            binding.btnNormalLog.alpha = enabledAlpha
            binding.btnDeepLog.alpha   = if (RootHelper.isRooted()) enabledAlpha else disabledAlpha
        }
    }

    private fun showPermissionDialog() {
        val dialog = ReadLogsPermissionDialog()
        dialog.onPermissionGranted = {
            updatePermStatusCard()
            Toast.makeText(this, getString(R.string.perm_granted_ok), Toast.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, "ReadLogsPermissionDialog")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val iconColor = typedValue.data
        for (i in 0 until menu.size()) { menu.getItem(i).icon?.setTint(iconColor) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings    -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.menu_info        -> { startActivity(Intent(this, InfoActivity::class.java)); true }
            R.id.menu_log_history -> { startActivity(Intent(this, LogHistoryActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupLogButtons() {
        binding.btnSoftLog.setOnClickListener   { checkPermAndStart(LogType.SOFT) }
        binding.btnNormalLog.setOnClickListener { checkPermAndStart(LogType.NORMAL) }
        binding.btnDeepLog.setOnClickListener   { checkPermAndStart(LogType.DEEP) }
        binding.btnStopLog.setOnClickListener   { stopLog() }
    }

    private fun checkPermAndStart(logType: LogType) {
        if (!RootHelper.hasReadLogsPermission(this)) {
            Toast.makeText(this, getString(R.string.no_readlogs_permission), Toast.LENGTH_LONG).show()
            showPermissionDialog()
            return
        }

        if (logType == LogType.DEEP) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.deep_log_warning_title))
                .setMessage(getString(R.string.deep_log_warning_message))
                .setPositiveButton(getString(R.string.btn_continue)) { _, _ -> startLog(logType) }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
            return
        }

        startLog(logType)
    }

    private fun startLog(logType: LogType) {
        if (LogService.isRunning) {
            Toast.makeText(this, getString(R.string.log_already_running), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, LogService::class.java).apply {
            action = LogService.ACTION_START
            putExtra(LogService.EXTRA_LOG_TYPE, logType.name)
        }
        startForegroundService(intent)
        updateLogButtons(isRunning = true)
        Toast.makeText(this, when (logType) {
                LogType.SOFT  -> getString(R.string.soft_log_title)
                LogType.NORMAL -> getString(R.string.normal_log_title)
                LogType.DEEP  -> getString(R.string.deep_log_title)
            } + " " + getString(R.string.log_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopLog() {
        if (!LogService.isRunning) {
            Toast.makeText(this, getString(R.string.no_active_log), Toast.LENGTH_SHORT).show()
            return
        }
        stopService(Intent(this, LogService::class.java).apply { action = LogService.ACTION_STOP })
        updateLogButtons(isRunning = false)
        Toast.makeText(this, getString(R.string.log_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun updateLogButtons(isRunning: Boolean) {
        val hasPermission = RootHelper.hasReadLogsPermission(this)
        val canStart = !isRunning && hasPermission

        val canStartDeep = !isRunning && RootHelper.isRooted()

        binding.btnSoftLog.isEnabled   = canStart
        binding.btnNormalLog.isEnabled = canStart
        binding.btnDeepLog.isEnabled   = canStartDeep
        binding.btnStopLog.isEnabled   = isRunning

        val disabledAlpha = 0.38f
        val enabledAlpha  = 1.0f
        binding.btnSoftLog.alpha   = if (canStart) enabledAlpha else disabledAlpha
        binding.btnNormalLog.alpha = if (canStart) enabledAlpha else disabledAlpha
        binding.btnDeepLog.alpha   = if (canStartDeep) enabledAlpha else disabledAlpha
        binding.btnStopLog.alpha   = if (isRunning) enabledAlpha else disabledAlpha

        if (isRunning) {
            binding.tvStatus.text = getString(R.string.status_recording)
            binding.tvStatus.setTextColor(getColor(R.color.green_soft))
        } else {
            binding.tvStatus.text = getString(R.string.status_idle)
            binding.tvStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun setupAiButton() {
        binding.btnAskAi.setOnClickListener {
            val dialog = AiPopupDialog()
            dialog.onRequestSent = { historyItem ->
                PrefsHelper.addAiHistoryItem(this, historyItem)
                refreshAiHistory()
            }
            dialog.onResultReceived = { refreshAiHistory() }
            dialog.show(supportFragmentManager, "AiPopupDialog")
        }
    }

    private fun setupAiHistoryList() {
        aiHistoryAdapter = AiHistoryAdapter(mutableListOf()) { item ->
            AiAnswerDialog.newInstance(item.answer, item.problem)
                .show(supportFragmentManager, "AiAnswerDialog")
        }
        binding.rvAiHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = aiHistoryAdapter
        }
        refreshAiHistory()
        binding.btnClearHistory.setOnClickListener { clearAiHistory() }
    }

    private fun clearAiHistory() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_clear_history_title))
            .setMessage(getString(R.string.ai_clear_history_confirm))
            .setPositiveButton(getString(R.string.ai_clear_history_yes)) { _, _ ->
                PrefsHelper.clearAiHistory(this)
                refreshAiHistory()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun refreshAiHistory() {
        val history = PrefsHelper.getAiHistory(this)
        aiHistoryAdapter.updateItems(history)
        binding.tvAiHistoryEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.rvAiHistory.visibility      = if (history.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun applyThemeFromPrefs() {
        val mode = when (PrefsHelper.getTheme(this)) {
            1    -> AppCompatDelegate.MODE_NIGHT_NO
            2    -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
