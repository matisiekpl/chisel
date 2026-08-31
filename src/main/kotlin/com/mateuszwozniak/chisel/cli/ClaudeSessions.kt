package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mateuszwozniak.chisel.model.TerminalSession
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.bool
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.name
import kotlin.streams.asSequence

object ClaudeSessions {

    fun list(projectPath: String?): List<TerminalSession> {
        val directory = directoryFor(projectPath) ?: return emptyList()
        if (!Files.isDirectory(directory)) return emptyList()
        return runCatching {
            Files.list(directory).use { paths ->
                paths.asSequence()
                    .filter { it.name.endsWith(SUFFIX) }
                    .map { read(it) }
                    .filterNotNull()
                    .sortedByDescending { it.modifiedAt }
                    .take(LIMIT)
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    fun transcriptOf(projectPath: String?, sessionId: String): List<TranscriptItem> {
        val directory = directoryFor(projectPath) ?: return emptyList()
        return SessionTranscript(directory.resolve(sessionId + SUFFIX)).read()
    }

    fun delete(projectPath: String?, sessionId: String) {
        val directory = directoryFor(projectPath) ?: return
        runCatching { Files.deleteIfExists(directory.resolve(sessionId + SUFFIX)) }
    }

    private fun directoryFor(projectPath: String?): Path? {
        val path = projectPath?.takeIf { it.isNotBlank() } ?: return null
        val slug = path.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        return Paths.get(System.getProperty("user.home"), ".claude", "projects", slug)
    }

    private fun read(file: Path): TerminalSession? {
        val id = file.name.removeSuffix(SUFFIX)
        val modifiedAt = runCatching { Files.getLastModifiedTime(file).toInstant() }.getOrNull()
            ?: Instant.EPOCH
        return TerminalSession(id, firstPrompt(file) ?: id, modifiedAt)
    }

    private fun firstPrompt(file: Path): String? = runCatching {
        Files.lines(file).use { lines ->
            lines.limit(SCAN_LINES)
                .map { runCatching { JsonParser.parseString(it) as? JsonObject }.getOrNull() }
                .filter { it != null }
                .map { promptOf(it!!) }
                .filter { it != null }
                .findFirst()
                .orElse(null)
        }
    }.getOrNull()

    private fun promptOf(root: JsonObject): String? {
        if (root.string("type") != "user" || root.bool("isSidechain")) return null
        val content = root.obj("message")?.get("content") ?: return null
        val text = when {
            content.isJsonPrimitive -> content.asString
            content.isJsonArray -> content.asJsonArray
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("type") == "text" }
                ?.string("text")

            else -> null
        }
        return text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val SUFFIX = ".jsonl"

    private const val LIMIT = 30

    private const val SCAN_LINES = 200L
}
