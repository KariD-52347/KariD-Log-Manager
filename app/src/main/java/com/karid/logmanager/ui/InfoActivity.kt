package com.karid.logmanager.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.karid.logmanager.R
import com.karid.logmanager.databinding.ActivityInfoBinding
import com.karid.logmanager.databinding.InfoTableRowBinding
import com.karid.logmanager.utils.RootHelper

class InfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInfoBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.karid.logmanager.utils.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.info_title)
        fillTable()
    }

    private val YES get() = "\u2705"
    private val NO  get() = "\u274C"

    private fun fillTable() {
        data class Row(val label: String, val l: String, val n: String, val d: String)

        val rows = listOf(
            Row("Fatal",                                     YES, YES, YES),
            Row("Error",                                     YES, YES, YES),
            Row("Warning",                                   YES, YES, YES),
            Row("Info",                                      NO,  YES, YES),
            Row("Debug",                                     NO,  YES, YES),
            Row("Verbose",                                   NO,  YES, YES),
            Row(getString(R.string.info_table_kernel),      NO,  NO,  YES),
            Row(getString(R.string.info_table_selinux),     NO,  NO,  YES),
            Row(getString(R.string.info_table_root_req),    NO,  NO,  YES)
        )

        val rowBindings = listOf(
            binding.rowFatal,
            binding.rowError,
            binding.rowWarning,
            binding.rowInfo,
            binding.rowDebug,
            binding.rowVerbose,
            binding.rowKernel,
            binding.rowSelinux,
            binding.rowRootReq
        )

        rows.forEachIndexed { i, row ->
            val rb = rowBindings[i]
            rb.tvRowLabel.text = row.label
            rb.tvCol1.text     = row.l
            rb.tvCol2.text     = row.n
            rb.tvCol3.text     = row.d
            if (i % 2 != 0) {
                rb.root.setBackgroundColor(
                    resources.getColor(
                        com.google.android.material.R.color.material_on_surface_emphasis_medium,
                        theme
                    ) and 0x22FFFFFF
                )
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return super.onOptionsItemSelected(item)
    }
}
