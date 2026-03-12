package com.karid.logmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.karid.logmanager.R
import com.karid.logmanager.databinding.DialogReadlogsPermissionBinding
import com.karid.logmanager.utils.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadLogsPermissionDialog : DialogFragment() {

    var onPermissionGranted: (() -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    private var _binding: DialogReadlogsPermissionBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReadlogsPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pkg = requireContext().packageName
        val adbCommand = "adb shell pm grant $pkg android.permission.READ_LOGS"
        binding.tvAdbCommand.text = adbCommand

        if (RootHelper.isRooted()) {
            binding.btnGrantRoot.visibility = View.VISIBLE
            binding.tvRootInfoNoRoot.visibility = View.GONE
            binding.btnGrantRoot.setOnClickListener {
                binding.btnGrantRoot.isEnabled = false
                binding.btnGrantRoot.text = getString(R.string.perm_granting)
                CoroutineScope(Dispatchers.Main).launch {
                    val success = withContext(Dispatchers.IO) {
                        RootHelper.grantReadLogsViaRoot(requireContext())
                    }
                    if (_binding == null) return@launch
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.perm_granted_root), Toast.LENGTH_SHORT).show()
                        onPermissionGranted?.invoke()
                        dismiss()
                    } else {
                        binding.btnGrantRoot.isEnabled = true
                        binding.btnGrantRoot.text = getString(R.string.perm_grant_root)
                        Toast.makeText(requireContext(), getString(R.string.perm_grant_root_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            binding.btnGrantRoot.visibility = View.GONE
            binding.tvRootInfoNoRoot.visibility = View.VISIBLE
        }

        binding.btnCopyAdb.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", adbCommand))
            Toast.makeText(requireContext(), getString(R.string.perm_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnCheckPermission.setOnClickListener {
            if (RootHelper.hasReadLogsPermission(requireContext())) {
                Toast.makeText(requireContext(), getString(R.string.perm_granted_ok), Toast.LENGTH_SHORT).show()
                onPermissionGranted?.invoke()
                dismiss()
            } else {
                Toast.makeText(requireContext(), getString(R.string.perm_not_yet), Toast.LENGTH_SHORT).show()
            }
        }

        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onDismissed?.invoke()
                dismiss()
                true
            } else false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
