package com.whisperbook.app.diagnostics

import java.io.File
import java.time.Instant

internal data class DiagnosticBuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val commit: String,
    val dirty: Boolean,
)

/** A bounded newline-delimited JSON log which is safe to call from a crash handler. */
internal class DiagnosticLogStore(
    private val directory: File,
    private val build: DiagnosticBuildIdentity,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val archiveCount: Int = DEFAULT_ARCHIVE_COUNT,
) {
    private val activeFile = File(directory, ACTIVE_FILE_NAME)

    @Synchronized
    fun append(
        level: String,
        event: String,
        details: Map<String, Any?> = emptyMap(),
        failure: Throwable? = null,
    ) {
        runCatching {
            ensureDirectory()
            val line = encodeLine(level, event, details, failure)
            rotateIfNeeded(line.toByteArray(Charsets.UTF_8).size + 1L)
            activeFile.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized
    fun snapshot(destination: File): File {
        ensureDirectory()
        destination.parentFile?.mkdirs()
        destination.outputStream().buffered().use { output ->
            logFilesOldestFirst().forEach { source ->
                if (!source.isFile) return@forEach
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            }
        }
        return destination
    }

    private fun encodeLine(
        level: String,
        event: String,
        details: Map<String, Any?>,
        failure: Throwable?,
    ): String {
        val fields = linkedMapOf<String, Any?>(
            "timestamp" to Instant.ofEpochMilli(nowEpochMs()).toString(),
            "level" to level,
            "event" to event,
            "version_name" to build.versionName,
            "version_code" to build.versionCode,
            "commit" to build.commit,
            "dirty_build" to build.dirty,
        )
        if (details.isNotEmpty()) fields["details"] = details.toSortedMap()
        if (failure != null) fields["exception"] = failure.stackTraceForDiagnostics()
        return fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:${jsonValue(value)}"
        }
    }

    private fun rotateIfNeeded(incomingBytes: Long) {
        if (!activeFile.exists() || activeFile.length() + incomingBytes <= maxFileBytes) return
        for (index in archiveCount downTo 1) {
            val target = archiveFile(index)
            val source = if (index == 1) activeFile else archiveFile(index - 1)
            if (target.exists()) target.delete()
            if (source.exists()) source.renameTo(target)
        }
    }

    private fun logFilesOldestFirst(): List<File> = buildList {
        for (index in archiveCount downTo 1) add(archiveFile(index))
        add(activeFile)
    }

    private fun archiveFile(index: Int) = File(directory, "beta-diagnostics.$index.jsonl")

    private fun ensureDirectory() {
        check(directory.exists() || directory.mkdirs()) { "Could not create diagnostics directory" }
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> if (value.isFinite()) value.toString() else jsonString(value.toString())
        is Double -> if (value.isFinite()) value.toString() else jsonString(value.toString())
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${jsonString(key.toString())}:${jsonValue(item)}"
        }
        else -> jsonString(value.toString())
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun Throwable.stackTraceForDiagnostics(): String {
        val failure = this
        return buildString {
            var current: Throwable? = failure
            var causeDepth = 0
            while (current != null && causeDepth < MAX_CAUSE_DEPTH && length < MAX_EXCEPTION_CHARS) {
                if (causeDepth > 0) append("Caused by: ")
                append(current.javaClass.name)
                append('\n')
                current.stackTrace.take(MAX_STACK_FRAMES_PER_CAUSE).forEach { frame ->
                    append("\tat ")
                    append(frame)
                    append('\n')
                }
                current = current.cause?.takeUnless { it === current }
                causeDepth += 1
            }
        }.take(MAX_EXCEPTION_CHARS)
    }

    private companion object {
        const val ACTIVE_FILE_NAME = "beta-diagnostics.jsonl"
        const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L
        const val DEFAULT_ARCHIVE_COUNT = 2
        const val MAX_EXCEPTION_CHARS = 24_000
        const val MAX_CAUSE_DEPTH = 8
        const val MAX_STACK_FRAMES_PER_CAUSE = 120
    }
}
