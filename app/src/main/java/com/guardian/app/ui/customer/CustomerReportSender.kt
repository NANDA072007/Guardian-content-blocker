package com.guardian.app.ui.customer

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CustomerReportSender {

    private const val FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLSco9XSW8OpzyR3ty03T-iUMQ6dAa6LhaJzeVihmrIx4OuhL6A/formResponse"
    private const val TIMEOUT_MS = 15_000

    suspend fun send(report: CustomerReport): Result {
        return try {
            val postData = buildPostData(report)
            val responseCode = executePost(postData)
            when (responseCode) {
                200, 302 -> Result.Success
                in 400..499 -> Result.Failure("Request rejected (HTTP $responseCode)")
                else -> Result.Failure("Server error (HTTP $responseCode)")
            }
        } catch (e: java.net.UnknownHostException) {
            Result.Failure("No internet connection")
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure("Request timed out")
        } catch (e: Exception) {
            Result.Failure("Could not connect: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun buildPostData(report: CustomerReport): String {
        val params = mutableMapOf(
            "entry.961950914" to report.description,
            "entry.1688901146" to buildSuggestionText(report),
            "entry.608655461" to report.deviceInfo?.toFormattedString().orEmpty()
        )
        return params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private fun buildSuggestionText(report: CustomerReport): String {
        return buildString {
            appendLine("Category: ${report.category.displayName}")
            appendLine("Severity: ${report.severity.displayName}")
            if (report.stepsToReproduce.isNotBlank()) {
                appendLine()
                appendLine("=== Steps to Reproduce ===")
                appendLine(report.stepsToReproduce)
            }
            if (report.expectedBehavior.isNotBlank()) {
                appendLine()
                appendLine("=== Expected Behavior ===")
                appendLine(report.expectedBehavior)
            }
            if (report.actualBehavior.isNotBlank()) {
                appendLine()
                appendLine("=== Actual Behavior ===")
                appendLine(report.actualBehavior)
            }
            if (report.recentEvents.isNotEmpty()) {
                appendLine()
                appendLine("=== Recent Events ===")
                report.recentEvents.forEach { appendLine(it) }
            }
        }
    }

    private fun executePost(postData: String): Int {
        val url = URL(FORM_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Connection", "close")

        try {
            val writer = OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(postData)
            writer.flush()
            writer.close()
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: String) : Result()
    }
}
