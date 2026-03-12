package com.karid.logmanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.karid.logmanager.R
import com.karid.logmanager.databinding.ActivityLogHistoryBinding
import com.karid.logmanager.utils.PrefsHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogHistoryBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.karid.logmanager.utils.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.log_history_title)

        loadLogFiles()
    }

    private fun loadLogFiles() {
        val logFiles = mutableListOf<LogEntry>()

        val treeUri = PrefsHelper.getSaveUri(this)
        if (treeUri != null) {
            val dir = DocumentFile.fromTreeUri(this, treeUri)
            dir?.listFiles()
                ?.filter { it.name?.endsWith(".txt") == true && it.name?.contains("log") == true }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10)
                ?.forEach { docFile ->
                    logFiles.add(
                        LogEntry(
                            name = docFile.name ?: "log.txt",
                            uri = docFile.uri,
                            filePath = null,
                            size = docFile.length(),
                            date = docFile.lastModified()
                        )
                    )
                }
        }

        if (logFiles.isEmpty()) {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            dir.listFiles { f -> f.name.endsWith(".txt") && f.name.contains("log") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10)
                ?.forEach { f ->
                    logFiles.add(
                        LogEntry(
                            name = f.name,
                            uri = null,
                            filePath = f.absolutePath,
                            size = f.length(),
                            date = f.lastModified()
                        )
                    )
                }
        }

        if (logFiles.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.scrollView.visibility = View.GONE
            return
        }

        binding.tvEmpty.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE

        logFiles.forEach { entry ->
            addLogCard(entry)
        }
    }

    private fun addLogCard(entry: LogEntry) {
        val card = layoutInflater.inflate(R.layout.item_log_history, binding.logContainer, false)

        card.findViewById<TextView>(R.id.tvLogName).text = entry.name
        val sizeMb = String.format("%.2f", entry.size / (1024.0 * 1024.0))
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(entry.date))
        card.findViewById<TextView>(R.id.tvLogInfo).text = "$sizeMb MB • $dateStr"

        card.findViewById<View>(R.id.btnOpen).setOnClickListener {
            openLog(entry)
        }

        card.findViewById<View>(R.id.btnShare).setOnClickListener {
            shareLog(entry)
        }

        binding.logContainer.addView(card)
    }

    private fun openLog(entry: LogEntry) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (entry.uri != null) {
                    setDataAndType(entry.uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else if (entry.filePath != null) {
                    val file = File(entry.filePath)
                    val uri = FileProvider.getUriForFile(
                        this@LogHistoryActivity,
                        "${packageName}.provider", file
                    )
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareLog(entry: LogEntry) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (entry.uri != null) {
                    putExtra(Intent.EXTRA_STREAM, entry.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else if (entry.filePath != null) {
                    val file = File(entry.filePath)
                    val uri = FileProvider.getUriForFile(
                        this@LogHistoryActivity,
                        "${packageName}.provider", file
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_log)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return super.onOptionsItemSelected(item)
    }

    data class LogEntry(
        val name: String,
        val uri: Uri?,
        val filePath: String?,
        val size: Long,
        val date: Long
    )
}
