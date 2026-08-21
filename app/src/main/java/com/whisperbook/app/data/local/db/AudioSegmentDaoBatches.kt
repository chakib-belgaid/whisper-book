package com.whisperbook.app.data.local.db

/**
 * Android SQLite builds can reject statements with more than 999 bound variables. Keep enough
 * headroom for DAO queries that also bind scalar arguments and use the same conservative batch
 * size as playback cache lookup.
 */
private const val SQL_QUERY_BATCH_SIZE = 900

suspend fun AudioSegmentDao.deleteForPassageIdsBatched(passageIds: Collection<String>) {
    passageIds.chunked(SQL_QUERY_BATCH_SIZE).forEach { batch ->
        deleteForPassageIdsBatch(batch)
    }
}
