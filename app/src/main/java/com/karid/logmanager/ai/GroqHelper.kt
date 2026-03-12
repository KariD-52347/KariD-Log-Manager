package com.karid.logmanager.ai

import com.karid.logmanager.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GroqHelper {

    private val API_KEYS by lazy {
        listOf(
            BuildConfig.GROQ_API_KEY_1,
            BuildConfig.GROQ_API_KEY_2,
            BuildConfig.GROQ_API_KEY_3
        ).filter { it.isNotEmpty() }
    }

    private const val MODEL    = "llama-3.3-70b-versatile"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeLog(
        logContent: String,
        userProblem: String,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {

        val isTurkish = language == "tr"

        val trimmedLog = logContent.trim()
        val logLines = trimmedLog.lines().filter { it.isNotBlank() }
        val headerLineCount = logLines.count { line ->
            line.startsWith("=") || line.startsWith("  KariD") ||
            line.startsWith("[ ") ||
            line == "LOG START" || line == "LOG BASLANGICI" ||
            line.trim().startsWith("Android sürümü:") || line.trim().startsWith("Android version:") ||
            line.trim().startsWith("Cihaz:") || line.trim().startsWith("Device:") ||
            line.trim().startsWith("Model:") || line.trim().startsWith("Uygulama:") ||
            line.trim().startsWith("Kayit turu:") || line.trim().startsWith("Recording type:")
        }
        val isEffectivelyEmpty = logLines.size <= headerLineCount + 15

        if (isEffectivelyEmpty) {
            val emptyResponse = if (isTurkish)
                "Hafif kayıt dosyasında herhangi bir hata tespit edemedim.\n\n" +
                "Bu durum şu anlama gelebilir:\n" +
                "• Kayıt süresi çok kısa tutulmuş olabilir — sorunu tetiklerken kaydın açık olduğundan emin olun.\n" +
                "• Sorun bu kayıt modunda görünür değil olabilir.\n\n" +
                "Öneriler:\n" +
                "1. Normal Kayıt modunu deneyin — tüm uygulama davranışlarını kaydeder.\n" +
                "2. Derin Kayıt modunu deneyin — donanım ve sistem seviyesindeki sorunları da yakalar (root gerektirir).\n" +
                "3. Kaydı başlattıktan sonra sorunu en az 2-3 kez tekrar etmeye çalışın."
            else
                "No errors were detected in this Light log file.\n\n" +
                "This could mean:\n" +
                "• The recording duration may have been too short — make sure recording is active while reproducing the issue.\n" +
                "• The problem may not be visible at this recording level.\n\n" +
                "Suggestions:\n" +
                "1. Try Normal Log mode — it captures all app behavior.\n" +
                "2. Try Deep Log mode — it also captures hardware and system-level issues (requires root).\n" +
                "3. After starting the recording, try to reproduce the issue at least 2-3 times."
            return@withContext Result.success(emptyResponse)
        }

        val langInstruction = if (isTurkish)
            """ÖNEMLI: Bu mesaja YALNIZCA TÜRKÇE yanıt ver. İngilizce kelime veya cümle KULLANMA.
Düz metin yaz, markdown (**, ##, - liste vb.) KULLANMA."""
        else
            """IMPORTANT: Answer ONLY in ENGLISH. Do NOT use any other language.
Use plain text only, no markdown (no **, ##, bullet lists etc.)."""

        val prompt = if (isTurkish) """
$langInstruction

Sen deneyimli bir Android sistem analistisin.

KULLANICININ SORUN AÇIKLAMASI:
$userProblem

LOG DOSYASI:
---
${smartTrimLog(logContent)}
---

Lütfen şunları belirt:
1. Olası Kök Neden
2. Kaynak (uygulama/sistem/donanım)
3. Çözüm Adımları
4. Özet
        """.trimIndent()
        else """
$langInstruction

You are an experienced Android system analyst.

USER PROBLEM DESCRIPTION:
$userProblem

LOG FILE:
---
${smartTrimLog(logContent)}
---

Please specify:
1. Likely Root Cause
2. Source (app/system/hardware)
3. Solution Steps
4. Summary
        """.trimIndent()

        var lastError = "Bilinmeyen hata"
        for (attempt in 0 until 6) {
            val apiKey = API_KEYS[attempt % API_KEYS.size]
            try {
                val result = callApi(apiKey, prompt)
                if (result.isSuccess) return@withContext result
                val errMsg = result.exceptionOrNull()?.message ?: ""
                lastError = errMsg
                if (errMsg != "RATE_LIMIT" && errMsg != "INVALID_KEY") {
                    return@withContext Result.failure(Exception(
                        if (isTurkish) "Yapay zeka hatası: $errMsg"
                        else "AI error: $errMsg"
                    ))
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Bilinmeyen hata"
            }
        }

        val limitMsg = if (isTurkish)
            "Yapay zeka limiti şuan dolu. Lütfen 15 dakika sonra tekrar deneyin.\n(Hata: $lastError)"
        else
            "AI limit is currently full. Please try again in 15 minutes.\n(Error: $lastError)"
        Result.failure(Exception(limitMsg))
    }

    private fun callApi(apiKey: String, prompt: String): Result<String> {
        val bodyJson = buildRequestBody(prompt)
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            return when (response.code) {
                200 -> {
                    val text = extractText(responseBody)
                    if (text != null) Result.success(text)
                    else Result.failure(Exception("HTTP 200 ama yanıt ayrıştırılamadı"))
                }
                429 -> Result.failure(Exception("RATE_LIMIT"))
                401 -> Result.failure(Exception("INVALID_KEY"))
                else -> Result.failure(Exception("HTTP ${response.code}: ${responseBody?.take(200)}"))
            }
        }
    }

    private fun buildRequestBody(prompt: String): String {
        val root = JsonObject()
        root.addProperty("model", MODEL)
        root.addProperty("temperature", 0.4)
        root.addProperty("max_tokens", 2048)
        val messages = com.google.gson.JsonArray()
        val msg = JsonObject()
        msg.addProperty("role", "user")
        msg.addProperty("content", prompt)
        messages.add(msg)
        root.add("messages", messages)
        return gson.toJson(root)
    }

    private fun extractText(json: String?): String? {
        if (json == null) return null
        return try {
            val root = gson.fromJson(json, JsonObject::class.java)
            root.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
        } catch (e: Exception) { null }
    }

    private fun smartTrimLog(log: String): String {
        val TARGET = 40_000
        if (log.length <= TARGET) return log

        val lines = log.lines()
        val header = lines.take(50).joinToString("\n")
        val errorLines = lines.filter { line ->
            line.contains(" E/") || line.contains(" W/") || line.contains(" F/") ||
            line.contains("Exception", ignoreCase = true) ||
            line.contains("FATAL", ignoreCase = true) ||
            line.contains("Error", ignoreCase = true) ||
            line.contains("ANR", ignoreCase = true)
        }.takeLast(200).joinToString("\n")
        val tail = lines.takeLast(100).joinToString("\n")

        val combined = "=== HEADER ===\n$header\n\n=== ERRORS ===\n$errorLines\n\n=== LAST 100 LINES ===\n$tail"
        return if (combined.length > TARGET) combined.take(TARGET) else combined
    }
}
