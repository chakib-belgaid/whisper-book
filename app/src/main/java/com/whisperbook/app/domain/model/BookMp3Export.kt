package com.whisperbook.app.domain.model

import androidx.compose.runtime.Immutable

enum class BookMp3ExportStage { PREPARING_AUDIO, ENCODING_MP3, SAVING }

@Immutable
data class BookMp3ExportProgress(
    val stage: BookMp3ExportStage,
    val progressFraction: Float,
    val chapterNumber: Int = 0,
    val totalChapters: Int = 0,
) {
    init {
        require(progressFraction.isFinite() && progressFraction in 0f..1f)
        require(chapterNumber >= 0)
        require(totalChapters >= 0)
        require(chapterNumber <= totalChapters || totalChapters == 0)
    }
}
@Immutable
data class BookMp3ExportResult(
    val chapterCount: Int,
    val durationMs: Long,
    val bytesWritten: Long,
) {
    init {
        require(chapterCount > 0)
        require(durationMs >= 0L)
        require(bytesWritten > 0L)
    }
}
