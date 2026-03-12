package com.karid.logmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karid.logmanager.service.LogService

class StopLogReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_LOG = "com.karid.logmanager.STOP_LOG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_LOG) {
            val stopIntent = Intent(context, LogService::class.java).apply {
                action = LogService.ACTION_STOP
            }
            context.stopService(stopIntent)

            val mainIntent = Intent().apply {
                setClassName(context.packageName, "${context.packageName}.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_notification", true)
            }
            context.startActivity(mainIntent)
        }
    }
}
