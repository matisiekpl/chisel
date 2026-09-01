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
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    fun transcriptOf(projectPath: String?, sessionId: String): List<TranscriptItem> {
        val directory = directoryFor(projectPath) ?: return emptyList()
        return SessionTranscript(directory.resolve(sessionId + SUFFIX)).read()
    }

    fun titleOf(projectPath: String?, sessionId: String): String? {
        val directory = directoryFor(projectPath) ?: return null
        val file = directory.resolve(sessionId + SUFFIX)
        if (!Files.isReadable(file)) return null
        return runCatching {
            Files.lines(file).use { lines ->
                lines.map { line ->
                    (runCatching { JsonParser.parseString(line) as? JsonObject }.getOrNull())
                        ?.takeIf { it.string("type") == "ai-title" }
                        ?.string("aiTitle")
                }.filter { it != null }.reduce { _, last -> last }.orElse(null)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun promptTitleOf(projectPath: String?, sessionId: String): String? {
        val directory = directoryFor(projectPath) ?: return null
        return firstPrompt(directory.resolve(sessionId + SUFFIX))
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
        if (root.string("type") != "user" || root.bool("isSidechain") || root.bool("isMeta")) return null
        val content = root.obj("message")?.get("content") ?: return null
        val texts = when {
            content.isJsonPrimitive -> listOf(content.asString)
            content.isJsonArray -> content.asJsonArray
                .mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "text" }
                .mapNotNull { it.string("text") }

            else -> emptyList()
        }
        return texts.firstNotNullOfOrNull { promptLine(it) }
    }

    private fun promptLine(text: String): String? {
        if (isGenerated(text)) return null
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !isGenerated(it) }
    }

    fun isGenerated(text: String): Boolean = GENERATED.containsMatchIn(text.trimStart())

    private const val SUFFIX = ".jsonl"

    private const val SCAN_LINES = 200L

    private val GENERATED = Regex(
        "^</?(local-command-[a-z]+|command-(name|message|args|contents)|" +
            "system-reminder|user-prompt-submit-hook|task-notification)>",
    )
}
