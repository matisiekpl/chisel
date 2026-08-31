package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.ContentBlock
import com.mateuszwozniak.chisel.protocol.bool
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

class SessionTranscript(private val file: Path) {

    private val counter = AtomicLong()
    private val toolCalls = mutableMapOf<String, TranscriptItem.ToolCall>()
    private val items = mutableListOf<TranscriptItem>()

    fun read(): List<TranscriptItem> {
        if (!Files.isReadable(file)) return emptyList()
        runCatching {
            Files.readAllLines(file).forEach { line ->
                (runCatching { JsonParser.parseString(line) }.getOrNull() as? JsonObject)?.let(::consume)
            }
        }
        return items.toList()
    }

    private fun consume(entry: JsonObject) {
        if (entry.bool("isSidechain") || entry.bool("isMeta")) return
        val message = entry.obj("message") ?: return
        when (entry.string("type")) {
            "user" -> consumeUser(entry, message)
            "assistant" -> consumeAssistant(entry, message)
            else -> Unit
        }
    }

    private fun consumeUser(entry: JsonObject, message: JsonObject) {
        val content = message.get("content") ?: return
        if (content.isJsonPrimitive) {
            appendPrompt(content.asString, entry.string("uuid"))
            return
        }
        if (!content.isJsonArray) return
        ContentBlock.parseList(content.asJsonArray).forEach { block ->
            when (block) {
                is ContentBlock.Text -> appendPrompt(block.text, entry.string("uuid"))
                is ContentBlock.ToolResult -> settleResult(block)
                else -> Unit
            }
        }
    }

    private fun consumeAssistant(entry: JsonObject, message: JsonObject) {
        val content = message.get("content")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val parent = entry.string("parentToolUseId")
        ContentBlock.parseList(content).forEach { block ->
            when (block) {
                is ContentBlock.Text -> items.add(TranscriptItem.AssistantText(nextId(), block.text))

                is ContentBlock.ToolUse -> {
                    val call = TranscriptItem.ToolCall(nextId(), block.id, block.name, block.input, parent)
                    toolCalls[block.id] = call
                    items.add(call)
                }

                else -> Unit
            }
        }
    }

    private fun appendPrompt(text: String, uuid: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(COMMAND_PREFIX)) return
        items.add(TranscriptItem.UserPrompt(nextId(), trimmed, uuid))
    }

    private fun settleResult(block: ContentBlock.ToolResult) {
        val call = toolCalls[block.toolUseId] ?: return
        call.result = block.text
        call.failed = block.isError
    }

    private fun nextId(): String = "restored-${counter.incrementAndGet()}"

    private companion object {
        const val COMMAND_PREFIX = "<"
    }
}
