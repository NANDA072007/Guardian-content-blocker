package com.guardian.app.walls.wall2.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityHealthEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foregroundAppChecker: ForegroundAppChecker,
    private val oemHealthChecker: OemHealthChecker
) {
    private val tag = "HealthEngine"

    private val _state = MutableStateFlow(HealthState.UNKNOWN)
    val state: StateFlow<HealthState> = _state.asStateFlow()

    private val _signals = MutableStateFlow<Map<String, HealthSignal>>(emptyMap())
    val signals: StateFlow<Map<String, HealthSignal>> = _signals.asStateFlow()

    private val checker = Handler(Looper.getMainLooper())
    private val evaluationIntervalMs = 15_000L
    private var evaluationRunning = false

    private var serviceConnected = false
    private var connectionCount = 0
    private var interruptCount = 0
    private var lastInterruptTime = 0L
    private var lastEventTime = 0L
    private var totalEventCount = 0L
    private val eventTimestamps = ArrayDeque<Long>()
    private var urlAttemptCount = 0L
    private var urlSuccessCount = 0L
    private var urlLastSuccessTime = 0L
    private var overlayShowCount = 0L
    private var overlayFailCount = 0L
    private var overlayLastShowTime = 0L
    private var degradedSince: Long? = null
    private var recoveryAttempts = 0

    fun recordSignal(key: String, signal: HealthSignal) {
        _signals.value = _signals.value + (key to signal)
    }

    fun onServiceConnected() {
        serviceConnected = true
        connectionCount++
        recordSignal("service_connected", HealthSignal.ServiceConnected(
            timestamp = now(),
            connected = true,
            connectionCount = connectionCount,
            lastInterruptTime = lastInterruptTime,
            interruptCount = interruptCount
        ))
        startPeriodicEvaluation()
    }

    fun onServiceDisconnected() {
        serviceConnected = false
        recordSignal("service_connected", HealthSignal.ServiceConnected(
            timestamp = now(),
            connected = false,
            connectionCount = connectionCount,
            lastInterruptTime = lastInterruptTime,
            interruptCount = interruptCount
        ))
    }

    fun onInterrupt() {
        interruptCount++
        lastInterruptTime = now()
    }

    fun onEvent(eventType: Int, packageName: String?, hasSource: Boolean, hasText: Boolean) {
        val now = now()
        lastEventTime = now
        totalEventCount++
        eventTimestamps.addLast(now)
        while (eventTimestamps.size > 100) eventTimestamps.removeFirst()

        val windowMs = 60_000L
        val cutoff = now - windowMs
        while (eventTimestamps.isNotEmpty() && eventTimestamps.first() < cutoff) {
            eventTimestamps.removeFirst()
        }
        val epm = if (eventTimestamps.size <= 1) 0.0
        else {
            val span = eventTimestamps.last() - eventTimestamps.first()
            if (span <= 0) 0.0 else (eventTimestamps.size.toDouble() / span) * 60_000.0
        }

        recordSignal("event_heartbeat", HealthSignal.EventHeartbeat(
            timestamp = now,
            eventType = eventType,
            packageName = packageName,
            hasSource = hasSource,
            hasText = hasText,
            eventCount = totalEventCount,
            eventsPerMinute = epm
        ))
    }

    fun onUrlExtraction(success: Boolean, url: String?, method: String?) {
        urlAttemptCount++
        if (success) {
            urlSuccessCount++
            urlLastSuccessTime = now()
        }
        recordSignal("url_extraction", HealthSignal.UrlExtraction(
            timestamp = now(),
            attemptCount = urlAttemptCount,
            successCount = urlSuccessCount,
            lastSuccessTime = urlLastSuccessTime,
            lastUrl = url,
            extractionMethod = method
        ))
    }

    fun onOverlayShown(success: Boolean) {
        if (success) {
            overlayShowCount++
            overlayLastShowTime = now()
        } else {
            overlayFailCount++
        }
        val overlayGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        } catch (_: Exception) { false }

        recordSignal("overlay", HealthSignal.OverlayCapability(
            timestamp = now(),
            overlayShown = success,
            systemAlertWindowGranted = overlayGranted,
            lastShowTime = overlayLastShowTime,
            showSuccessCount = overlayShowCount,
            showFailCount = overlayFailCount
        ))
    }

    fun onSelfTest(passed: Boolean, details: SelfTestDetails) {
        recordSignal("self_test", HealthSignal.SelfTestResult(
            timestamp = now(),
            passed = passed,
            chromeDetected = details.chromeDetected,
            urlExtracted = details.urlExtracted,
            activeWindowReadable = details.activeWindowReadable,
            recentEventsReceived = details.recentEventsReceived,
            overallHealthy = passed
        ))
    }

    private fun startPeriodicEvaluation() {
        if (evaluationRunning) return
        evaluationRunning = true
        checker.post(object : Runnable {
            override fun run() {
                evaluateHealth()
                checker.postDelayed(this, evaluationIntervalMs)
            }
        })
    }

    fun evaluateHealth(): HealthState {
        val now = now()

        val foregroundSignal = foregroundAppChecker.checkAgainstAccessibility(
            accessibilityPackage = (_signals.value["event_heartbeat"] as? HealthSignal.EventHeartbeat)?.packageName
        )
        recordSignal("foreground_app", foregroundSignal)

        val oemSignal = oemHealthChecker.check()
        recordSignal("oem_status", oemSignal)

        val signals = _signals.value

        val failing = mutableListOf<String>()
        var passed = 0
        var total = 0

        fun check(name: String, condition: () -> Boolean) {
            total++
            if (condition()) passed++ else failing.add(name)
        }

        val connectionSignal = signals["service_connected"] as? HealthSignal.ServiceConnected
        val eventSignal = signals["event_heartbeat"] as? HealthSignal.EventHeartbeat
        val urlSignal = signals["url_extraction"] as? HealthSignal.UrlExtraction
        val overlaySignal = signals["overlay"] as? HealthSignal.OverlayCapability
        val selfTestSignal = signals["self_test"] as? HealthSignal.SelfTestResult
        val windowSignal = signals["active_window"] as? HealthSignal.ActiveWindow

        check("Service connected") { serviceConnected }
        check("Events received") { totalEventCount > 0 }
        check("Recent event (60s)") { (now - lastEventTime) < 60_000 }
        check("Not excessive interrupts") { interruptCount < 10 }
        check("Package not null") { eventSignal?.packageName != null }
        check("Events per minute normal") {
            val epm = eventSignal?.eventsPerMinute ?: 0.0
            epm < 5000.0
        }
        check("Has accessibility source") { eventSignal?.hasSource == true }
        check("Foreground match") { foregroundSignal.match != false }
        check("Root window available") { windowSignal?.rootAvailable != false }
        check("URL extraction attempted") { urlAttemptCount > 0 }
        check("Overlay permission granted") { overlaySignal?.systemAlertWindowGranted != false }

        val score = if (total == 0) 0f else passed.toFloat() / total.toFloat()

        val level = when {
            !serviceConnected -> HealthLevel.UNAVAILABLE
            score >= 0.8f && totalEventCount > 0 -> HealthLevel.HEALTHY
            score >= 0.5f && totalEventCount > 0 -> HealthLevel.DEGRADED
            score == 0f -> HealthLevel.UNKNOWN
            else -> HealthLevel.DEGRADED
        }

        if (level == HealthLevel.DEGRADED && degradedSince == null) {
            degradedSince = now
        } else if (level != HealthLevel.DEGRADED) {
            degradedSince = null
        }

        val newState = HealthState(
            level = level,
            score = score,
            failingChecks = failing,
            lastCheckedAt = now,
            degradedSince = degradedSince,
            recoveryAttempts = recoveryAttempts
        )

        _state.value = newState
        return newState
    }

    fun needsRecovery(): Boolean {
        val s = _state.value
        return s.level == HealthLevel.DEGRADED && (degradedSince != null && now() - degradedSince!! > 120_000L)
    }

    fun recordRecoveryAttempt() {
        recoveryAttempts++
    }

    fun resetDegradedTimer() {
        degradedSince = null
    }

    private fun now() = System.currentTimeMillis()

    data class SelfTestDetails(
        val chromeDetected: Boolean,
        val urlExtracted: Boolean,
        val activeWindowReadable: Boolean,
        val recentEventsReceived: Boolean
    )
}
