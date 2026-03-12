package com.karid.logmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.karid.logmanager.R
import com.karid.logmanager.model.LogType
import com.karid.logmanager.receiver.StopLogReceiver
import com.karid.logmanager.utils.DeviceInfoHelper
import com.karid.logmanager.utils.PrefsHelper
import com.karid.logmanager.utils.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogService : Service() {

    companion object {
        const val ACTION_START     = "com.karid.logmanager.START_LOG"
        const val ACTION_STOP      = "com.karid.logmanager.STOP_LOG"
        const val EXTRA_LOG_TYPE   = "log_type"

        private const val CHANNEL_ID = "karid_log_channel"
        const val NOTIF_ID           = 1001

        var isRunning = false
            private set
        var currentLogType: LogType? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var logJob: Job? = null
    private var logProcess: Process? = null

    private var tempFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val logTypeName = intent.getStringExtra(EXTRA_LOG_TYPE)
                    ?: return START_NOT_STICKY
                val logType = LogType.valueOf(logTypeName)
                if (isRunning) return START_NOT_STICKY
                startLogging(logType)
            }
            ACTION_STOP -> {
                stopLogging()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startLogging(logType: LogType) {
        isRunning = true
        currentLogType = logType

        startForeground(NOTIF_ID, buildNotification(logType))

        logJob = serviceScope.launch {
            try {
                streamLog(logType)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun streamLog(logType: LogType) = withContext(Dispatchers.IO) {
        val header = DeviceInfoHelper.buildHeader(applicationContext)

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${logType.name.lowercase()}_log_$dateStr.txt"
        val cacheDir = applicationContext.cacheDir
        val tmp = File(cacheDir, fileName)
        tempFile = tmp

        val isRooted = RootHelper.isRooted()
        if (isRooted) {
            try { Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -c")).waitFor() } catch (_: Exception) {}
        } else {
            try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (_: Exception) {}
        }

        tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(header)
            writer.flush()

            when (logType) {
                LogType.SOFT -> {
                    if (isRooted) streamLogcatAsRoot(writer, "logcat *:S *:E *:W *:F") { isActive }
                    else streamLogcat(writer, arrayOf("logcat", "*:S", "*:E", "*:W", "*:F")) { isActive }
                }
                LogType.NORMAL -> {
                    if (isRooted) streamLogcatAsRoot(writer, "logcat") { isActive }
                    else streamLogcat(writer, arrayOf("logcat")) { isActive }
                }
                LogType.DEEP -> {
                    writer.write("\n### FULL LOGCAT (stream) ###\n\n")
                    writer.flush()
                    streamLogcatAsRoot(writer, "logcat") { isActive }

                    writer.write("\n\n### KERNEL LOG (dmesg) ###\n\n")
                    writer.flush()
                    writer.write(RootHelper.runAsRoot("dmesg"))

                    writer.write("\n\n### SELINUX LOG ###\n\n")
                    writer.flush()
                    writer.write(RootHelper.runAsRoot("dmesg | grep -i 'avc\\|selinux'"))
                }
            }
        }

        saveToDestination(tmp, fileName)
    }

    private fun streamLogcatAsRoot(writer: BufferedWriter, cmd: String, isActive: () -> Boolean) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            logProcess = process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var emptyCount = 0
            while (isActive()) {
                val line = reader.readLine()
                if (line == null) {
                    emptyCount++
                    if (emptyCount > 3) break
                    Thread.sleep(100)
                    continue
                }
                emptyCount = 0
                writer.write(line)
                writer.newLine()
                if (!reader.ready()) writer.flush()
            }
            reader.close()
            process.destroy()
        } catch (_: Exception) {}
    }

    private fun streamLogcat(writer: BufferedWriter, cmd: Array<String>, isActive: () -> Boolean) {
        try {
            val process = Runtime.getRuntime().exec(cmd)
            logProcess = process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var emptyCount = 0
            while (isActive()) {
                val line = reader.readLine()
                if (line == null) {
                    emptyCount++
                    if (emptyCount > 3) break
                    Thread.sleep(100)
                    continue
                }
                emptyCount = 0
                writer.write(line)
                writer.newLine()
                if (!reader.ready()) writer.flush()
            }
            reader.close()
            process.destroy()
        } catch (_: Exception) {}
    }

    private fun dumpLogcatToWriter(writer: BufferedWriter, softFilter: Boolean) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
            logProcess = process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                if (softFilter) {
                    if (line.contains(" E ") || line.contains(" W ") || 
                        line.contains(" F ") || line.startsWith("-----")) {
                        writer.write(line)
                        writer.newLine()
                    }
                } else {
                    writer.write(line)
                    writer.newLine()
                }
            }
            writer.flush()
            reader.close()
            process.destroy()
        } catch (_: Exception) {}
    }

    private fun stopLogging() {
        isRunning = false
        currentLogType = null

        logProcess?.destroy()
        logProcess = null

        logJob?.cancel()
        logJob = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun saveToDestination(src: File, fileName: String) {
        val treeUri: Uri? = PrefsHelper.getSaveUri(applicationContext)
        var saved = false

        if (treeUri != null) {
            try {
                val dir  = DocumentFile.fromTreeUri(applicationContext, treeUri)
                val dest = dir?.createFile("text/plain", fileName)
                dest?.uri?.let { uri ->
                    applicationContext.contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().copyTo(out)
                        saved = true
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (!saved) {
            try {
                val destFile = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                src.copyTo(destFile, overwrite = true)
            } catch (e: Exception) { e.printStackTrace() }
        }

        src.delete()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description    = getString(R.string.notif_channel_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(logType: LogType): Notification {
        val stopPi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, StopLogReceiver::class.java).apply {
                action = StopLogReceiver.ACTION_STOP_LOG
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainPi = PendingIntent.getActivity(
            this, 0,
            Intent().apply {
                setClassName(packageName, "$packageName.MainActivity")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = when (logType) {
            LogType.SOFT   -> getString(R.string.btn_soft_log)
            LogType.NORMAL -> getString(R.string.btn_normal_log)
            LogType.DEEP   -> getString(R.string.btn_deep_log)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_log_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("$typeLabel – ${getString(R.string.notif_running)}")
            .setSubText(getString(R.string.app_name))
            .setContentIntent(mainPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.notif_stop_button),
                stopPi
            )
            .build()
    }

    override fun onDestroy() {
        stopLogging()
        super.onDestroy()
    }
}
