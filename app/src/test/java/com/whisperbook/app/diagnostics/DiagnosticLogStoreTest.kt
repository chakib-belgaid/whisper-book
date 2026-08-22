package com.whisperbook.app.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogStoreTest {
    @Test
    fun everyEventCarriesBuildIdentityAndEscapesDetails() {
        withTemporaryDirectory { directory ->
            val store = DiagnosticLogStore(
                directory = directory,
                build = DiagnosticBuildIdentity("0.2-beta", 7, "abc123def456", true),
                nowEpochMs = { 1_700_000_000_000L },
            )

            store.append(
                level = "INFO",
                event = "test_event",
                details = mapOf("line" to "one\ntwo", "ok" to true),
            )

            val line = File(directory, "beta-diagnostics.jsonl").readText()
            assertTrue(line.contains("\"version_name\":\"0.2-beta\""))
            assertTrue(line.contains("\"version_code\":7"))
            assertTrue(line.contains("\"commit\":\"abc123def456\""))
            assertTrue(line.contains("\"dirty_build\":true"))
            assertTrue(line.contains("\"line\":\"one\\ntwo\""))
        }
    }

    @Test
    fun exceptionMessagesAreExcludedToAvoidLeakingBookData() {
        withTemporaryDirectory { directory ->
            val store = DiagnosticLogStore(
                directory = directory,
                build = DiagnosticBuildIdentity("0.1", 1, "abc", false),
            )

            store.append(
                level = "ERROR",
                event = "failed",
                failure = IllegalStateException("Private Book Title at /secret/book.epub"),
            )

            val line = File(directory, "beta-diagnostics.jsonl").readText()
            assertTrue(line.contains("java.lang.IllegalStateException"))
            assertFalse(line.contains("Private Book Title"))
            assertFalse(line.contains("/secret/book.epub"))
        }
    }

    @Test
    fun rotatesOldLogsAndSnapshotsThemInChronologicalOrder() {
        withTemporaryDirectory { directory ->
            var timestamp = 1_700_000_000_000L
            val store = DiagnosticLogStore(
                directory = directory,
                build = DiagnosticBuildIdentity("0.1", 1, "abc", false),
                nowEpochMs = { timestamp++ },
                maxFileBytes = 220,
                archiveCount = 2,
            )
            repeat(6) { index ->
                store.append("INFO", "event_$index", mapOf("padding" to "x".repeat(80)))
            }
            val snapshot = store.snapshot(File(directory, "snapshot.jsonl"))
            val events = snapshot.readLines().mapNotNull { line ->
                Regex("\\\"event\\\":\\\"([^\\\"]+)\\\"").find(line)?.groupValues?.get(1)
            }

            assertEquals(listOf("event_3", "event_4", "event_5"), events)
        }
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("whisperbook-diagnostics-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
