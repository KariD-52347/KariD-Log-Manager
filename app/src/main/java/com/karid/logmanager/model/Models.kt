package com.karid.logmanager.model

import java.util.UUID

enum class LogType(val displayName: String) {
    SOFT("Soft Log"),
    NORMAL("Normal Log"),
    DEEP("Deep Log")
}

enum class AiStatus {
    SENDING,
    ANSWERED,
    ERROR
}

data class AiHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val problem: String = "",
    var statusName: String = AiStatus.SENDING.name,
    var answer: String = "",
    val logFile: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {

    fun getStatus(): AiStatus = try {
        AiStatus.valueOf(statusName)
    } catch (e: Exception) {
        AiStatus.ERROR
    }
}
