package com.whisperbook.app.engine.document

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Owns the app-private Markdown sidecar generated for an imported PDF or EPUB. */
object PublicationMarkdownFiles {
    fun forSource(sourceFile: File): File {
        val parent = requireNotNull(sourceFile.parentFile) { "The publication source has no parent directory" }
        return File(parent, "${sourceFile.nameWithoutExtension}.md")
    }

    fun write(sourceFile: File, markdown: String): File {
        require(sourceFile.isFile) { "The publication source is unavailable" }
        require(markdown.isNotBlank()) { "The publication Markdown is empty" }
        val destination = forSource(sourceFile)
        val temporary = File.createTempFile("${destination.name}.", ".pending", destination.parentFile)
        try {
            temporary.writeText(markdown, Charsets.UTF_8)
            runCatching {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return destination
        } finally {
            temporary.delete()
        }
    }
}
