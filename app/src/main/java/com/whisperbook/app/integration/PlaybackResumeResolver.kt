package com.whisperbook.app.integration

internal data class SavedPlaybackResume(
    val passageId: String,
    val segmentId: String,
    val segmentPositionMs: Long,
)

internal data class PlannedPlaybackSegment(
    val passageId: String,
    val sourcePassageId: String,
    val segmentId: String,
)

internal data class ResolvedPlaybackResume(
    val passageId: String?,
    val segmentId: String?,
    val segmentPositionMs: Long,
)

/** Maps persisted playback onto the current narration chunk plan without reusing stale offsets. */
internal fun resolvePlaybackResumeTarget(
    checkpoint: SavedPlaybackResume?,
    currentPassageId: String?,
    plannedSegments: List<PlannedPlaybackSegment>,
): ResolvedPlaybackResume {
    if (checkpoint != null) {
        plannedSegments.firstOrNull { it.segmentId == checkpoint.segmentId }?.let { exact ->
            return ResolvedPlaybackResume(
                passageId = exact.passageId,
                segmentId = exact.segmentId,
                segmentPositionMs = checkpoint.segmentPositionMs.coerceAtLeast(0L),
            )
        }

        val legacySourcePassageId = checkpoint.passageId.substringBefore(CHUNK_ID_DELIMITER)
        plannedSegments.firstOrNull { planned ->
            planned.sourcePassageId == legacySourcePassageId ||
                planned.passageId == checkpoint.passageId
        }?.let { migrated ->
            return ResolvedPlaybackResume(
                passageId = migrated.passageId,
                segmentId = migrated.segmentId,
                segmentPositionMs = 0L,
            )
        }
    }

    val current = currentPassageId?.let { passageId ->
        val sourcePassageId = passageId.substringBefore(CHUNK_ID_DELIMITER)
        plannedSegments.firstOrNull { planned ->
            planned.passageId == passageId || planned.sourcePassageId == sourcePassageId
        }
    }
    return ResolvedPlaybackResume(
        passageId = current?.passageId,
        segmentId = current?.segmentId,
        segmentPositionMs = 0L,
    )
}

private const val CHUNK_ID_DELIMITER = "::chunk:"
