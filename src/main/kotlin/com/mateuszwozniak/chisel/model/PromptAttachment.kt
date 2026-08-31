package com.mateuszwozniak.chisel.model

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

data class PromptAttachment(val path: String, val mediaType: String?) {

    val name: String get() = Paths.get(path).fileName.toString()

    val isImage: Boolean get() = mediaType != null

    fun encode(): String? = runCatching {
        Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(path)))
    }.getOrNull()

    companion object {

        private val MEDIA_TYPES = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
        )

        fun of(path: Path): PromptAttachment? {
            if (!Files.isReadable(path) || Files.isDirectory(path)) return null
            return PromptAttachment(path.toString(), mediaTypeOf(path.fileName.toString()))
        }

        fun isImage(path: String): Boolean = mediaTypeOf(Paths.get(path).fileName.toString()) != null

        private fun mediaTypeOf(name: String): String? =
            MEDIA_TYPES[name.substringAfterLast('.', "").lowercase()]
    }
}
