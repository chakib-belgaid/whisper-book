package com.whisperbook.app.domain

import android.net.Uri
import com.whisperbook.app.domain.model.BookMp3ExportProgress
import com.whisperbook.app.domain.model.BookMp3ExportResult

interface BookMp3Exporter {
    suspend fun export(
        bookId: String,
        destination: Uri,
        onProgress: (BookMp3ExportProgress) -> Unit = {},
    ): BookMp3ExportResult
}
