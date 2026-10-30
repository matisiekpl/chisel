package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mateuszwozniak.chisel.model.TerminalSession
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.bool
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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
                lines.filter { it.contains(AI_TITLE) }.map { line ->
                    (runCatching { JsonParser.parseString(line) as? JsonObject }.getOrNull())
                        ?.takeIf { it.string("type") == "ai-title" }
                        ?.string("aiTitle")
                }.filter { it != null }.reduce { _, last -> last }.orElse(null)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun promptTitleOf(projectPath: String?, sessionId: String): String? {
        val directory = directoryFor(projectPath) ?: return null
        val file = directory.resolve(sessionId + SUFFIX)
        return firstPrompt(file) ?: firstCommand(file)
    }

    fun delete(projectPath: String?, sessionId: String) {
        val directory = directoryFor(projectPath) ?: return
        runCatching { Files.deleteIfExists(directory.resolve(sessionId + SUFFIX)) }
    }

    fun markInteractive(projectPath: String?, sessionId: String) {
        val directory = directoryFor(projectPath) ?: return
        val file = directory.resolve(sessionId + SUFFIX)
        if (!Files.isWritable(file)) return
        runCatching {
            val recorded = Files.readString(file)
            if (!recorded.contains(PRINT_ENTRYPOINT)) return
            val rewritten = recorded.replace(PRINT_ENTRYPOINT, TERMINAL_ENTRYPOINT)
                .toByteArray(StandardCharsets.UTF_8)
            FileChannel.open(file, StandardOpenOption.WRITE).use { channel ->
                channel.write(ByteBuffer.wrap(rewritten))
                channel.truncate(rewritten.size.toLong())
            }
        }
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
        return TerminalSession(id, firstPrompt(file) ?: firstCommand(file) ?: id, modifiedAt)
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

    private fun firstCommand(file: Path): String? = runCatching {
        Files.lines(file).use { lines ->
            lines.limit(SCAN_LINES).asSequence()
                .mapNotNull { runCatching { JsonParser.parseString(it) as? JsonObject }.getOrNull() }
                .mapNotNull { textsOf(it)?.joinToString("\n") }
                .zipWithNext()
                .firstOrNull { (text, next) -> COMMAND_NAME.containsMatchIn(text) && !next.contains(LOCAL_OUTPUT) }
                ?.first
                ?.let { text ->
                    val name = COMMAND_NAME.find(text)?.groupValues?.get(1) ?: return@let null
                    val arguments = COMMAND_ARGUMENTS.find(text)?.groupValues?.get(1).orEmpty()
                    "$name $arguments".trim()
                }
        }
    }.getOrNull()

    private fun promptOf(root: JsonObject): String? {
        if (root.bool("isMeta")) return null
        return textsOf(root)?.firstNotNullOfOrNull { promptLine(it) }
    }

    private fun textsOf(root: JsonObject): List<String>? {
        if (root.string("type") != "user" || root.bool("isSidechain")) return null
        val content = root.obj("message")?.get("content") ?: return null
        return when {
            content.isJsonPrimitive -> listOf(content.asString)
            content.isJsonArray -> content.asJsonArray
                .mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "text" }
                .mapNotNull { it.string("text") }

            else -> emptyList()
        }
    }

    fun promptLine(text: String): String? {
        if (isGenerated(text)) return null
        return text.lineSequence()
            .map { it.replace(NOISE, "").trim() }
            .firstOrNull { it.isNotEmpty() && !isGenerated(it) && !it.startsWith(INTERRUPTED) }
    }

    fun isGenerated(text: String): Boolean = GENERATED.containsMatchIn(text.trimStart())

    private const val SUFFIX = ".jsonl"

    private const val PRINT_ENTRYPOINT = "\"entrypoint\":\"sdk-cli\""

    private const val TERMINAL_ENTRYPOINT = "\"entrypoint\":\"cli\""

    private const val SCAN_LINES = 200L

    private const val AI_TITLE = "\"ai-title\""

    private val GENERATED = Regex(
        "^</?(local-command-[a-z]+|command-(name|message|args|contents)|" +
            "system-reminder|user-prompt-submit-hook|task-notification)>",
    )

    private const val INTERRUPTED = "[Request interrupted by user"

    private val NOISE = Regex("\\[Image #\\d+]|</?pasted_content[^>]*>")

    private const val LOCAL_OUTPUT = "<local-command-stdout>"

    private val COMMAND_NAME = Regex("<command-name>(/[^<]+)</command-name>")

    private val COMMAND_ARGUMENTS = Regex("<command-args>([^<]*)</command-args>")
}
