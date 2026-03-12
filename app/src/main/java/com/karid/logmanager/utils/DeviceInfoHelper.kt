package com.karid.logmanager.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object DeviceInfoHelper {

    fun buildHeader(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(60))
        sb.appendLine("  KariD Log Manager - Device Information")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        sb.appendLine("[ DEVICE & OS ]")
        sb.appendLine("Model          : ${Build.MODEL}")
        sb.appendLine("Manufacturer   : ${Build.MANUFACTURER}")
        sb.appendLine("Brand          : ${Build.BRAND}")
        sb.appendLine("Android        : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("CPU ABI        : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"}")
        sb.appendLine("Kernel         : ${System.getProperty("os.version") ?: "Unknown"}")
        sb.appendLine()

        sb.appendLine("[ ROM & BUILD ]")
        sb.appendLine("Build Display  : ${Build.DISPLAY}")
        sb.appendLine("Build Type     : ${Build.TYPE}")
        sb.appendLine("Build Tags     : ${Build.TAGS}")
        sb.appendLine("Security Patch : ${Build.VERSION.SECURITY_PATCH}")
        sb.appendLine()

        sb.appendLine("[ SECURITY ]")
        val vboot = getVerifiedBootState()
        if (vboot != null) sb.appendLine("Verified Boot  : $vboot")
        val selinux = getSELinuxMode()
        if (selinux != null) sb.appendLine("SELinux        : $selinux")
        sb.appendLine()

        sb.appendLine("[ ROOT ]")
        val isRooted = RootHelper.isRooted()
        sb.appendLine("Root           : ${if (isRooted) "Present" else "Not Present"}")
        if (isRooted) {
            val magiskVer = RootHelper.getMagiskVersion()
            if (magiskVer != null) sb.appendLine("Magisk         : $magiskVer")
            val zygisk = RootHelper.getZygiskStatus()
            if (zygisk != null) sb.appendLine("Zygisk         : $zygisk")
            val denylist = RootHelper.getDenyListStatus()
            if (denylist != null) sb.appendLine("DenyList       : $denylist")
            val modules = RootHelper.getMagiskModules()
            if (modules.isNotEmpty()) {
                sb.appendLine("Magisk Modules :")
                modules.forEach { sb.appendLine("  - $it") }
            }
        }
        sb.appendLine()

        sb.appendLine("[ GOOGLE ]")
        sb.appendLine("Play Services  : ${isPackageInstalled(context, "com.google.android.gms")}")
        sb.appendLine("Play Store     : ${isPackageInstalled(context, "com.android.vending")}")
        sb.appendLine()

        sb.appendLine("=".repeat(60))
        sb.appendLine("LOG START")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        return sb.toString()
    }

    private fun getVerifiedBootState(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.verifiedbootstate"))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.destroy()
            if (result.isNotEmpty()) result else null
        } catch (e: Exception) { null }
    }

    private fun getSELinuxMode(): String? {
        return try {
            val enforce = File("/sys/fs/selinux/enforce")
            if (!enforce.exists()) return null
            when (enforce.readText().trim()) {
                "1" -> "Enforcing"
                "0" -> "Permissive"
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private fun isPackageInstalled(context: Context, packageName: String): String {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            "Installed"
        } catch (e: PackageManager.NameNotFoundException) {
            "Not Installed"
        }
    }
}
