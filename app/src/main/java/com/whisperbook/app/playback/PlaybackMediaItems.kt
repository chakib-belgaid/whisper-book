package com.whisperbook.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class PlaybackMediaDescriptor(
    val bookId: String,
    val chapterId: String,
    val passageId: String,
    val segmentId: String,
    val segmentDurationMs: Long,
    val chapterStartMs: Long,
    val chapterDurationMs: Long,
)

internal object PlaybackMediaItems {
    private const val MEDIA_ID_PREFIX = "whisperbook-segment-v1"
    private const val KEY_BOOK_ID = "whisperbook.book_id"
    private const val KEY_CHAPTER_ID = "whisperbook.chapter_id"
    private const val KEY_PASSAGE_ID = "whisperbook.passage_id"
    private const val KEY_SEGMENT_ID = "whisperbook.segment_id"
    private const val KEY_SEGMENT_DURATION_MS = "whisperbook.segment_duration_ms"
    private const val KEY_CHAPTER_START_MS = "whisperbook.chapter_start_ms"
    private const val KEY_CHAPTER_DURATION_MS = "whisperbook.chapter_duration_ms"

    fun create(
        queue: PlaybackChapterQueue,
        fromSegmentIndex: Int = 0,
    ): List<MediaItem> {
        require(fromSegmentIndex in 0..queue.segments.size) {
            "fromSegmentIndex must point inside the queue or immediately after it"
        }
        val chapterDurationMs = queue.durationMs
        var chapterStartMs = queue.segments
            .take(fromSegmentIndex)
            .sumOf { it.audioSegment.durationMs.coerceAtLeast(0L) }
        return queue.segments.drop(fromSegmentIndex).map { playable ->
            val audio = playable.audioSegment
            val path = requireNotNull(audio.path)
            val file = File(path)
            require(file.isFile) { "Audio segment does not exist: $path" }
            val descriptor = PlaybackMediaDescriptor(
                bookId = queue.bookId,
                chapterId = queue.chapterId,
                passageId = playable.passageId,
                segmentId = audio.id,
                segmentDurationMs = audio.durationMs,
                chapterStartMs = chapterStartMs,
                chapterDurationMs = chapterDurationMs,
            )
            chapterStartMs += audio.durationMs

            MediaItem.Builder()
                .setMediaId(encodeMediaId(descriptor))
                .setUri(Uri.fromFile(file))
                .setMimeType(MimeTypes.AUDIO_WAV)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(queue.chapterTitle)
                        .setArtist(playable.speakerName)
                        .setAlbumTitle(queue.bookTitle)
                        .setTrackNumber(playable.passageOrdinal + 1)
                        .setDurationMs(audio.durationMs)
                        .setExtras(descriptor.toBundle())
                        .build(),
                )
                .build()
        }
    }

    fun descriptor(item: MediaItem?): PlaybackMediaDescriptor? {
        item ?: return null
        val extras = item.mediaMetadata.extras
        if (extras != null) {
            return extras.toDescriptorOrNull()
        }
        return decodeMediaId(item.mediaId)
    }

    fun encodeMediaId(descriptor: PlaybackMediaDescriptor): String = (
        listOf(MEDIA_ID_PREFIX) + listOf(
            descriptor.bookId,
            descriptor.chapterId,
            descriptor.passageId,
            descriptor.segmentId,
        ).map { component -> component.base64UrlEncode() }
    ).joinToString(":")

    private fun decodeMediaId(mediaId: String): PlaybackMediaDescriptor? {
        val parts = mediaId.split(':')
        if (parts.size != 5 || parts.first() != MEDIA_ID_PREFIX) return null
        return runCatching {
            PlaybackMediaDescriptor(
                bookId = parts[1].base64UrlDecode(),
                chapterId = parts[2].base64UrlDecode(),
                passageId = parts[3].base64UrlDecode(),
                segmentId = parts[4].base64UrlDecode(),
                segmentDurationMs = 0L,
                chapterStartMs = 0L,
                chapterDurationMs = 0L,
            )
        }.getOrNull()
    }

    private fun PlaybackMediaDescriptor.toBundle() = Bundle().apply {
        putString(KEY_BOOK_ID, bookId)
        putString(KEY_CHAPTER_ID, chapterId)
        putString(KEY_PASSAGE_ID, passageId)
        putString(KEY_SEGMENT_ID, segmentId)
        putLong(KEY_SEGMENT_DURATION_MS, segmentDurationMs)
        putLong(KEY_CHAPTER_START_MS, chapterStartMs)
        putLong(KEY_CHAPTER_DURATION_MS, chapterDurationMs)
    }

    private fun Bundle.toDescriptorOrNull(): PlaybackMediaDescriptor? = runCatching {
        PlaybackMediaDescriptor(
            bookId = requireNotNull(getString(KEY_BOOK_ID)),
            chapterId = requireNotNull(getString(KEY_CHAPTER_ID)),
            passageId = requireNotNull(getString(KEY_PASSAGE_ID)),
            segmentId = requireNotNull(getString(KEY_SEGMENT_ID)),
            segmentDurationMs = getLong(KEY_SEGMENT_DURATION_MS),
            chapterStartMs = getLong(KEY_CHAPTER_START_MS),
            chapterDurationMs = getLong(KEY_CHAPTER_DURATION_MS),
        )
    }.getOrNull()

    private fun String.base64UrlEncode(): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(toByteArray(StandardCharsets.UTF_8))

    private fun String.base64UrlDecode(): String = String(
        Base64.getUrlDecoder().decode(this),
        StandardCharsets.UTF_8,
    )
}
