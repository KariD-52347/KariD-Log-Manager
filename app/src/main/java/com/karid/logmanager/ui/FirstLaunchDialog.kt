package com.karid.logmanager.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.karid.logmanager.R
import com.karid.logmanager.databinding.DialogFirstLaunchBinding
import com.karid.logmanager.utils.PrefsHelper
import com.karid.logmanager.utils.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirstLaunchDialog : DialogFragment() {

    private var _binding: DialogFirstLaunchBinding? = null
    private val binding get() = _binding!!

    private var fileGranted = false
    private var notifGranted = false

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) markNotifGranted() }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            requireContext().contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PrefsHelper.setSaveUri(requireContext(), it)
            markFileGranted(it)
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) folderPickerLauncher.launch(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFirstLaunchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        val pkg = requireContext().packageName
        val adbCommand = "adb shell pm grant $pkg android.permission.READ_LOGS"
        binding.tvAdbCommandLaunch.text = adbCommand

        binding.btnClose.isEnabled = false
        binding.checkboxAgreement.setOnCheckedChangeListener { _, checked ->
            updateStartButton(checked)
        }

        updateReadLogsStatus()

        if (RootHelper.isRooted()) {
            binding.layoutLaunchRoot.visibility = View.VISIBLE
            if (!RootHelper.hasReadLogsPermission(requireContext())) {
                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        RootHelper.grantReadLogsViaRoot(requireContext())
                    }
                    if (_binding == null) return@launch
                    if (success) {
                        updateReadLogsStatus()
                        Toast.makeText(requireContext(), getString(R.string.perm_auto_granted_root), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            binding.btnGrantRootPerm.setOnClickListener {
                binding.btnGrantRootPerm.isEnabled = false
                binding.btnGrantRootPerm.text = getString(R.string.perm_granting)
                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        RootHelper.grantReadLogsViaRoot(requireContext())
                    }
                    if (_binding == null) return@launch
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.perm_granted_root), Toast.LENGTH_SHORT).show()
                        updateReadLogsStatus()
                        binding.btnGrantRootPerm.text = getString(R.string.perm_granted_ok)
                    } else {
                        binding.btnGrantRootPerm.isEnabled = true
                        binding.btnGrantRootPerm.text = getString(R.string.btn_grant_root_perm)
                        Toast.makeText(requireContext(), getString(R.string.perm_grant_root_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            binding.layoutLaunchRoot.visibility = View.GONE
        }

        binding.btnCopyAdbLaunch.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("adb", adbCommand))
            Toast.makeText(requireContext(), getString(R.string.perm_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnFilePermission.setOnClickListener { requestStoragePermission() }

        binding.btnNotificationPermission.setOnClickListener { requestNotificationPermission() }

        binding.btnClose.setOnClickListener {
            PrefsHelper.setFirstLaunchDone(requireContext())
            dismiss()
        }

        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                true
            } else false
        }

        checkExistingPermissions()
    }

    private fun updateReadLogsStatus() {
        val hasPermission = RootHelper.hasReadLogsPermission(requireContext())
        if (hasPermission) {
            val statusText = if (RootHelper.isRooted()) {
                getString(R.string.welcome_perm_status_granted_root)
            } else {
                getString(R.string.welcome_perm_status_granted_adb)
            }
            binding.tvReadLogsStatus.text = statusText
            binding.tvReadLogsStatus.setTextColor(requireContext().getColor(R.color.green_soft))
        } else {
            binding.tvReadLogsStatus.text = getString(R.string.welcome_perm_status_missing)
            binding.tvReadLogsStatus.setTextColor(requireContext().getColor(R.color.red_stop))
        }
    }

    private fun updateStartButton(checked: Boolean) {
        binding.btnClose.isEnabled = checked && fileGranted && notifGranted
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${requireContext().packageName}")
                        }
                    )
                } catch (_: Exception) { folderPickerLauncher.launch(null) }
            } else folderPickerLauncher.launch(null)
        } else folderPickerLauncher.launch(null)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else markNotifGranted()
    }

    private fun markFileGranted(uri: Uri) {
        fileGranted = true
        binding.btnFilePermission.text = getString(R.string.folder_selected)
        binding.btnFilePermission.isEnabled = false
        binding.tvSelectedFolder.text = uri.path
        binding.tvSelectedFolder.visibility = View.VISIBLE
        updateStartButton(binding.checkboxAgreement.isChecked)
    }

    private fun markNotifGranted() {
        notifGranted = true
        binding.btnNotificationPermission.text = getString(R.string.permission_granted)
        binding.btnNotificationPermission.isEnabled = false
        updateStartButton(binding.checkboxAgreement.isChecked)
    }

    private fun checkExistingPermissions() {
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        if (notifOk) markNotifGranted()

        val savedUri = PrefsHelper.getSaveUri(requireContext())
        if (savedUri != null) markFileGranted(savedUri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
