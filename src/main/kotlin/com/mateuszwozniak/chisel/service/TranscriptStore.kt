package com.mateuszwozniak.chisel.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.bool
import com.mateuszwozniak.chisel.protocol.number
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TranscriptStore(private val project: Project) {

    fun load(conversationId: String): List<TranscriptItem> {
        val file = fileFor(conversationId)
        if (!Files.exists(file)) return emptyList()
        return runCatching {
            Files.readAllLines(file).mapNotNull { line ->
                (runCatching { JsonParser.parseString(line) }.getOrNull() as? JsonObject)?.let(::toItem)
            }
        }.getOrDefault(emptyList())
    }

    fun save(conversation: Conversation) {
        val file = fileFor(conversation.id)
        runCatching {
            Files.createDirectories(file.parent)
            Files.write(file, conversation.transcript.map { toJson(it).toString() })
        }
    }

    fun delete(conversationId: String) {
        runCatching { Files.deleteIfExists(fileFor(conversationId)) }
    }

    private fun fileFor(conversationId: String): Path = Paths.get(
        PathManager.getSystemPath(),
        DATA_DIRECTORY,
        project.locationHash,
        "$conversationId.jsonl",
    )

    private fun toJson(item: TranscriptItem): JsonObject = JsonObject().apply {
        addProperty("id", item.id)
        when (item) {
            is TranscriptItem.UserPrompt -> {
                addProperty("kind", "user")
                addProperty("text", item.text)
                addProperty("uuid", item.messageUuid)
                add("attachments", JsonArray().apply { item.attachments.forEach(::add) })
            }

            is TranscriptItem.AssistantText -> {
                addProperty("kind", "assistant")
                addProperty("text", item.text)
            }

            is TranscriptItem.Thinking -> {
                addProperty("kind", "thinking")
                addProperty("text", item.text)
            }

            is TranscriptItem.ToolCall -> {
                addProperty("kind", "tool")
                addProperty("toolUseId", item.toolUseId)
                addProperty("name", item.name)
                add("input", item.input)
                addProperty("result", item.result)
                addProperty("failed", item.failed)
                addProperty("parent", item.parentToolUseId)
            }

            is TranscriptItem.Notice -> {
                addProperty("kind", "notice")
                addProperty("text", item.text)
                addProperty("failed", item.failed)
            }

            is TranscriptItem.TurnSummary -> {
                addProperty("kind", "summary")
                addProperty("cost", item.costUsd)
                addProperty("inputTokens", item.inputTokens)
                addProperty("outputTokens", item.outputTokens)
            }
        }
    }

    private fun toItem(json: JsonObject): TranscriptItem? {
        val id = json.string("id") ?: return null
        return when (json.string("kind")) {
            "user" -> TranscriptItem.UserPrompt(
                id,
                json.string("text").orEmpty(),
                json.string("uuid"),
                json.get("attachments")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { element -> element.isJsonPrimitive }?.asString }
                    .orEmpty(),
            )
            "assistant" -> TranscriptItem.AssistantText(id, json.string("text").orEmpty())
            "thinking" -> TranscriptItem.Thinking(id, json.string("text").orEmpty())

            "tool" -> TranscriptItem.ToolCall(
                id,
                json.string("toolUseId").orEmpty(),
                json.string("name").orEmpty(),
                json.obj("input") ?: JsonObject(),
                json.string("parent"),
            ).apply {
                result = json.string("result")
                failed = json.bool("failed")
            }

            "notice" -> TranscriptItem.Notice(id, json.string("text").orEmpty(), json.bool("failed"))

            "summary" -> TranscriptItem.TurnSummary(
                id,
                json.number("cost"),
                json.number("inputTokens")?.toLong(),
                json.number("outputTokens")?.toLong(),
            )

            else -> null
        }
    }

    private companion object {
        const val DATA_DIRECTORY = "chisel"
    }
}
