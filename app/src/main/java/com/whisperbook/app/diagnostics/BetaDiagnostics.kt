package com.whisperbook.app.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import androidx.core.content.FileProvider
import com.whisperbook.app.BuildConfig
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline beta telemetry. Nothing is uploaded: a tester must explicitly share a snapshot from
 * Settings. Event callers must only provide technical metadata, never book text, titles, or URIs.
 */
object BetaDiagnostics {
    private val build = DiagnosticBuildIdentity(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        commit = BuildConfig.GIT_COMMIT,
        dirty = BuildConfig.GIT_DIRTY,
    )
    private val processStartedAtMs = monotonicNowMs()
    private val firstFrameRecorded = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    private var store: DiagnosticLogStore? = null
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null
    private var crashHandlerInstalled = false

    private var synthesisCount = 0
    private var synthesisChars = 0L
    private var synthesisElapsedMs = 0L
    private var synthesisAudioMs = 0L
    private var worstSynthesisRtfMilli = 0L

    @Volatile
    private var uiPerformanceSnapshotter: (() -> Unit)? = null

    val versionLabel: String
        get() = "v${build.versionName} (${build.versionCode})"

    val commitId: String
        get() = build.commit

    val hasLocalChanges: Boolean
        get() = build.dirty

    fun initialize(context: Context) {
        if (store != null) return
        synchronized(lock) {
            if (store != null) return
            store = DiagnosticLogStore(File(context.filesDir, "diagnostics"), build)
            installCrashHandler()
            info(
                event = "session_start",
                details = mapOf(
                    "android_sdk" to Build.VERSION.SDK_INT,
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    "available_processors" to Runtime.getRuntime().availableProcessors(),
                    "native_heap_mb" to bytesToMb(Debug.getNativeHeapAllocatedSize()),
                ),
            )
        }
    }

    fun info(event: String, details: Map<String, Any?> = emptyMap()) {
        store?.append("INFO", event, details)
    }

    fun performance(event: String, details: Map<String, Any?> = emptyMap()) {
        store?.append("PERFORMANCE", event, details)
    }

    fun error(event: String, failure: Throwable, details: Map<String, Any?> = emptyMap()) {
        store?.append("ERROR", event, details, failure)
    }

    @Synchronized
    fun recordSynthesis(chars: Int, elapsedMs: Long, audioMs: Long, realTimeFactorMilli: Long) {
        synthesisCount += 1
        synthesisChars += chars.coerceAtLeast(0)
        synthesisElapsedMs += elapsedMs.coerceAtLeast(0L)
        synthesisAudioMs += audioMs.coerceAtLeast(0L)
        worstSynthesisRtfMilli = maxOf(worstSynthesisRtfMilli, realTimeFactorMilli)
        if (synthesisCount >= SYNTHESIS_BATCH_SIZE) flushSynthesisSummary()
    }

    @Synchronized
    fun flushSynthesisSummary() {
        if (synthesisCount == 0) return
        performance(
            "tts_synthesis_batch",
            mapOf(
                "samples" to synthesisCount,
                "characters" to synthesisChars,
                "elapsed_ms" to synthesisElapsedMs,
                "audio_ms" to synthesisAudioMs,
                "worst_rtf_milli" to worstSynthesisRtfMilli,
            ),
        )
        synthesisCount = 0
        synthesisChars = 0L
        synthesisElapsedMs = 0L
        synthesisAudioMs = 0L
        worstSynthesisRtfMilli = 0L
    }

    fun recordFirstFrame() {
        if (!firstFrameRecorded.compareAndSet(false, true)) return
        performance(
            "first_frame",
            mapOf("process_to_first_frame_ms" to monotonicNowMs() - processStartedAtMs),
        )
    }

    fun createShareChooser(context: Context): Intent {
        uiPerformanceSnapshotter?.invoke()
        flushSynthesisSummary()
        val shareDirectory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        shareDirectory.listFiles()?.forEach { oldSnapshot ->
            if (oldSnapshot.isFile) oldSnapshot.delete()
        }
        val safeVersion = build.versionName.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val snapshot = File(
            shareDirectory,
            "whisperbook-diagnostics-$safeVersion-${build.commit}-${Instant.now().epochSecond}.jsonl",
        )
        val report = checkNotNull(store) { "Diagnostics are not initialized" }.snapshot(snapshot)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            report,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Whisperbook beta diagnostics ${versionLabel} · ${build.commit}")
            clipData = ClipData.newRawUri("Whisperbook beta diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share beta diagnostics")
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            error(
                event = "uncaught_crash",
                failure = failure,
                details = mapOf("thread" to thread.name),
            )
            previousCrashHandler?.uncaughtException(thread, failure)
        }
        crashHandlerInstalled = true
    }

    internal fun currentMemoryDetails(): Map<String, Any?> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "java_heap_used_mb" to bytesToMb(runtime.totalMemory() - runtime.freeMemory()),
            "java_heap_max_mb" to bytesToMb(runtime.maxMemory()),
            "native_heap_mb" to bytesToMb(Debug.getNativeHeapAllocatedSize()),
        )
    }

    internal fun setUiPerformanceSnapshotter(snapshotter: (() -> Unit)?) {
        uiPerformanceSnapshotter = snapshotter
    }

    private fun bytesToMb(bytes: Long): Long = bytes.coerceAtLeast(0L) / (1024L * 1024L)

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    private const val SYNTHESIS_BATCH_SIZE = 20
}
