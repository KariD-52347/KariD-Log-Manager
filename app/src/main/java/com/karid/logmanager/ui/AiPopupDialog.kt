package com.karid.logmanager.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import com.karid.logmanager.R
import com.karid.logmanager.ai.GroqHelper
import com.karid.logmanager.databinding.DialogAiPopupBinding
import com.karid.logmanager.model.AiHistoryItem
import com.karid.logmanager.model.AiStatus
import com.karid.logmanager.utils.PrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AiPopupDialog : DialogFragment() {

    private var _binding: DialogAiPopupBinding? = null
    private val binding get() = _binding!!

    var onRequestSent: ((AiHistoryItem) -> Unit)? = null
    var onResultReceived: (() -> Unit)? = null

    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var logFiles: List<LogFileInfo> = emptyList()
    private var selectedIndex = 0

    data class LogFileInfo(
        val name: String,
        val uri: Uri?,
        val localPath: String?,
        val sizeBytes: Long,
        val lastModified: Long
    )

    companion object {
        private const val MAX_FILE_SIZE = 100L * 1024L
        private val LOG_PREFIXES = listOf("soft_log_")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiPopupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = true
        loadLogFiles()
        setupUI()
    }

    private fun loadLogFiles() {
        val result = mutableListOf<LogFileInfo>()

        val treeUri = PrefsHelper.getSaveUri(requireContext())
        if (treeUri != null) {
            try {
                DocumentFile.fromTreeUri(requireContext(), treeUri)
                    ?.listFiles()
                    ?.filter { doc ->
                        val name = doc.name ?: return@filter false
                        name.endsWith(".txt") && LOG_PREFIXES.any { name.startsWith(it) }
                    }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { doc ->
                        result.add(LogFileInfo(doc.name ?: "log.txt", doc.uri, null, doc.length(), doc.lastModified()))
                    }
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (result.isEmpty()) {
            try {
                android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    .listFiles { f -> f.name.endsWith(".txt") && LOG_PREFIXES.any { f.name.startsWith(it) } }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { f -> result.add(LogFileInfo(f.name, null, f.absolutePath, f.length(), f.lastModified())) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        try {
            requireContext().cacheDir
                .listFiles { f -> f.name.endsWith(".txt") && LOG_PREFIXES.any { f.name.startsWith(it) } }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { f ->
                    if (result.none { it.name == f.name })
                        result.add(LogFileInfo(f.name, null, f.absolutePath, f.length(), f.lastModified()))
                }
        } catch (_: Exception) {}

        logFiles = result
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnPrevLog.setOnClickListener {
            if (selectedIndex > 0) { selectedIndex--; updateSelectedFile() }
        }
        binding.btnNextLog.setOnClickListener {
            if (selectedIndex < logFiles.size - 1) { selectedIndex++; updateSelectedFile() }
        }
        binding.btnSendToAi.setOnClickListener { sendToAi() }

        if (logFiles.isEmpty()) {
            binding.tvNoLogs.visibility          = View.VISIBLE
            binding.layoutLogSelector.visibility = View.GONE
            binding.btnSendToAi.isEnabled        = false
        } else {
            binding.tvNoLogs.visibility          = View.GONE
            binding.layoutLogSelector.visibility = View.VISIBLE
            selectedIndex = 0
            updateSelectedFile()
        }
    }

    private fun updateSelectedFile() {
        if (logFiles.isEmpty()) return
        val f = logFiles[selectedIndex]
        binding.tvSelectedLog.text = f.name
        binding.tvFileSize.text    = "%.2f MB".format(f.sizeBytes / (1024.0 * 1024.0))

        if (f.sizeBytes > MAX_FILE_SIZE) {
            binding.tvSizeWarning.visibility = View.VISIBLE
            binding.btnSendToAi.isEnabled    = false
        } else {
            binding.tvSizeWarning.visibility = View.GONE
            binding.btnSendToAi.isEnabled    = true
        }
        binding.btnPrevLog.isEnabled = selectedIndex > 0
        binding.btnNextLog.isEnabled = selectedIndex < logFiles.size - 1
    }

    private fun sendToAi() {
        val problem = binding.etProblemDescription.text.toString().trim()
        if (problem.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.ai_enter_problem), Toast.LENGTH_SHORT).show()
            return
        }
        if (logFiles.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.ai_no_log_file), Toast.LENGTH_SHORT).show()
            return
        }
        val selected = logFiles[selectedIndex]
        if (selected.sizeBytes > MAX_FILE_SIZE) {
            Toast.makeText(requireContext(), getString(R.string.ai_file_too_large), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSendToAi.isEnabled  = false
        binding.btnClose.isEnabled     = false
        binding.progressBar.visibility = View.VISIBLE

        val historyItem = AiHistoryItem(
            problem    = problem,
            statusName = AiStatus.SENDING.name,
            logFile    = selected.name
        )

        val appCtx          = requireContext().applicationContext
        val lang            = resolvedLang(appCtx)
        val requestCallback = onRequestSent
        val resultCallback  = onResultReceived

        requestCallback?.invoke(historyItem)

        aiScope.launch {
            val logContent = withContext(Dispatchers.IO) { readFile(appCtx, selected) }
            val result     = GroqHelper.analyzeLog(logContent, problem, lang)

            result.onSuccess { answer ->
                PrefsHelper.updateAiHistoryItem(appCtx, historyItem.id, AiStatus.ANSWERED, answer)
                resultCallback?.invoke()
                if (isAdded && !isDetached) dismiss()
            }.onFailure { err ->
                val errMsg = err.message ?: "Bilinmeyen hata"
                PrefsHelper.updateAiHistoryItem(appCtx, historyItem.id, AiStatus.ANSWERED, errMsg)
                resultCallback?.invoke()
                if (isAdded && !isDetached) dismiss()
            }
        }
    }

    private fun readFile(ctx: Context, file: LogFileInfo): String {
        return try {
            if (file.uri != null) {
                ctx.contentResolver.openInputStream(file.uri)?.bufferedReader()?.readText() ?: ""
            } else if (file.localPath != null) {
                File(file.localPath).readText(Charsets.UTF_8)
            } else ""
        } catch (e: Exception) { "Log dosyasi okunamadi: ${e.message}" }
    }

    private fun resolvedLang(ctx: Context): String {
        val pref = PrefsHelper.getLanguage(ctx)
        return if (pref == "system")
            if (java.util.Locale.getDefault().language == "tr") "tr" else "en"
        else pref
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
