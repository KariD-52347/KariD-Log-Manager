package com.karid.logmanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.karid.logmanager.R
import com.karid.logmanager.databinding.ActivitySettingsBinding
import com.karid.logmanager.utils.LocaleHelper
import com.karid.logmanager.utils.PrefsHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                PrefsHelper.setSaveUri(this, uri)
                binding.tvSaveFolder.text = uri.path
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        setupThemeSelector()
        setupLanguageSelector()
        showSaveFolder()
    }

    private fun setupThemeSelector() {
        val themes = resources.getStringArray(R.array.theme_options)
        binding.spinnerTheme.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        binding.spinnerTheme.setSelection(PrefsHelper.getTheme(this))

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                PrefsHelper.setTheme(this@SettingsActivity, pos)
                val mode = when (pos) {
                    1    -> AppCompatDelegate.MODE_NIGHT_NO
                    2    -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupLanguageSelector() {
        val langs = resources.getStringArray(R.array.language_options)
        binding.spinnerLanguage.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, langs).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        val currentPos = when (PrefsHelper.getLanguage(this)) {
            "tr" -> 1
            "en" -> 2
            else -> 0
        }
        binding.spinnerLanguage.setSelection(currentPos)

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var firstTime = true

            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (firstTime) { firstTime = false; return }
                val lang = when (pos) { 1 -> "tr"; 2 -> "en"; else -> "system" }
                val prev = PrefsHelper.getLanguage(this@SettingsActivity)
                if (lang == prev) return
                PrefsHelper.setLanguage(this@SettingsActivity, lang)
                Toast.makeText(this@SettingsActivity,
                    getString(R.string.lang_change_restart), Toast.LENGTH_SHORT).show()
                LocaleHelper.applyAndRestart(this@SettingsActivity)
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun showSaveFolder() {
        val uri = PrefsHelper.getSaveUri(this)
        binding.tvSaveFolder.text = uri?.path ?: getString(R.string.no_folder_selected)

        binding.btnChangeFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return super.onOptionsItemSelected(item)
    }
}
