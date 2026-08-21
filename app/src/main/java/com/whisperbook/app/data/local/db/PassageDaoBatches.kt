package com.whisperbook.app.data.local.db

private const val SQL_QUERY_BATCH_SIZE = 900

suspend fun PassageDao.updateSpeakerAttributionBatched(
    passageIds: Collection<String>,
    speakerId: String,
    attributionRule: String,
): Int = passageIds.chunked(SQL_QUERY_BATCH_SIZE).sumOf { batch ->
    updateSpeakerAttribution(batch, speakerId, attributionRule)
}
