package com.karid.logmanager.utils

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object RootHelper {

    private val SU_PATHS = listOf(
        "/sbin/su", "/system/bin/su", "/system/xbin/su",
        "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
    )

    fun isRooted(): Boolean {
        for (path in SU_PATHS) { if (File(path).exists()) return true }
        if (File("/data/adb/magisk").exists()) return true
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = p.inputStream.bufferedReader().readText()
            p.destroy()
            result.contains("uid=0")
        } catch (_: Exception) { false }
    }

    fun hasReadLogsPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.READ_LOGS") ==
                PackageManager.PERMISSION_GRANTED
    }

    fun grantReadLogsViaRoot(context: Context): Boolean {
        return try {
            val pkg = context.packageName
            val p = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "pm grant $pkg android.permission.READ_LOGS")
            )
            p.waitFor()
            p.destroy()
            hasReadLogsPermission(context)
        } catch (_: Exception) { false }
    }

    fun getMagiskVersion(): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -v"))
            val result = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            if (result.isNotEmpty() && !result.contains("not found", ignoreCase = true)) result else null
        } catch (_: Exception) { null }
    }

    fun getZygiskStatus(): String? {
        return try {
            val p = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk'\"")
            )
            val result = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            when (result) { "1" -> "Active"; "0" -> "Passive"; else -> null }
        } catch (_: Exception) { null }
    }

    fun getDenyListStatus(): String? {
        return try {
            val p = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='denylist'\"")
            )
            val result = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            when (result) { "1" -> "Active"; "0" -> "Passive"; else -> null }
        } catch (_: Exception) { null }
    }

    fun getMagiskModules(): List<String> {
        return try {
            val modulesDir = File("/data/adb/modules")
            if (!modulesDir.exists()) return emptyList()
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /data/adb/modules"))
            val result = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            if (result.isEmpty()) emptyList() else result.split("\n").filter { it.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    fun runAsRoot(command: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = p.inputStream.bufferedReader().readText()
            val error  = p.errorStream.bufferedReader().readText()
            p.waitFor()
            p.destroy()
            if (error.isNotEmpty() && output.isEmpty()) error else output
        } catch (e: Exception) { "Root command failed: ${e.message}" }
    }
}
