package com.mateuszwozniak.chisel.cli

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.protocol.ContentBlock
import com.mateuszwozniak.chisel.protocol.IncomingFrame
import com.mateuszwozniak.chisel.protocol.PermissionRequest
import com.mateuszwozniak.chisel.protocol.StreamEvent
import com.mateuszwozniak.chisel.protocol.array
import com.mateuszwozniak.chisel.protocol.bool
import com.mateuszwozniak.chisel.protocol.number
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string

class StreamJsonCodec {

    private companion object {
        const val COMPACTING = "compacting"
    }

    private val gson = Gson()

    fun parseLine(line: String): IncomingFrame? {
        val root = runCatching { JsonParser.parseString(line) }.getOrNull() as? JsonObject ?: return null
        return when (root.string("type")) {
            "control_request" -> parsePermission(root)
            "control_response" -> parseControlAnswer(root)
            "control_cancel_request" ->
                root.string("request_id")?.let { IncomingFrame.ControlCancelled(it) }

            else -> parseEvent(root)?.let { IncomingFrame.Event(it) }
        }
    }

    fun userMessage(text: String, attachments: List<PromptAttachment>): String {
        val content = JsonArray()
        attachments.filter { it.isImage }.mapNotNull(::imageBlock).forEach(content::add)
        content.add(
            JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", withFileReferences(text, attachments.filterNot { it.isImage }))
            }
        )
        val message = JsonObject().apply {
            addProperty("role", "user")
            add("content", content)
        }
        val frame = JsonObject().apply {
            addProperty("type", "user")
            add("message", message)
            add("parent_tool_use_id", null)
        }
        return gson.toJson(frame)
    }

    private fun withFileReferences(text: String, files: List<PromptAttachment>): String {
        if (files.isEmpty()) return text
        val references = files.joinToString("\n") { "Attached file: " + it.path }
        return if (text.isBlank()) references else text + "\n\n" + references
    }

    private fun imageBlock(attachment: PromptAttachment): JsonObject? {
        val encoded = attachment.encode() ?: return null
        return JsonObject().apply {
            addProperty("type", "image")
            add(
                "source",
                JsonObject().apply {
                    addProperty("type", "base64")
                    addProperty("media_type", attachment.mediaType)
                    addProperty("data", encoded)
                },
            )
        }
    }

    fun controlRequest(requestId: String, request: JsonObject): String {
        val frame = JsonObject().apply {
            addProperty("type", "control_request")
            addProperty("request_id", requestId)
            add("request", request)
        }
        return gson.toJson(frame)
    }

    fun controlResponse(requestId: String, payload: JsonObject): String {
        val response = JsonObject().apply {
            addProperty("subtype", "success")
            addProperty("request_id", requestId)
            add("response", payload)
        }
        val frame = JsonObject().apply {
            addProperty("type", "control_response")
            add("response", response)
        }
        return gson.toJson(frame)
    }

    private fun parsePermission(root: JsonObject): IncomingFrame? {
        val requestId = root.string("request_id") ?: return null
        val request = root.obj("request") ?: return null
        if (request.string("subtype") != "can_use_tool") return null
        return IncomingFrame.Permission(
            PermissionRequest(
                requestId = requestId,
                toolName = request.string("tool_name").orEmpty(),
                toolUseId = request.string("tool_use_id"),
                input = request.obj("input") ?: JsonObject(),
                decisionReason = request.string("decision_reason"),
                blockedPath = request.string("blocked_path"),
            )
        )
    }

    private fun parseControlAnswer(root: JsonObject): IncomingFrame? {
        val response = root.obj("response") ?: return null
        val requestId = response.string("request_id") ?: return null
        val failed = response.string("subtype") == "error"
        return IncomingFrame.ControlAnswer(
            requestId = requestId,
            payload = response.obj("response"),
            error = if (failed) response.string("error") ?: "unknown error" else null,
        )
    }

    private fun parseEvent(root: JsonObject): StreamEvent? = when (root.string("type")) {
        "system" -> parseSystem(root)
        "stream_event" -> parseDelta(root)
        "assistant" -> StreamEvent.AssistantTurn(
            ContentBlock.parseContent(root.obj("message")?.get("content")),
            root.string("parent_tool_use_id"),
        )

        "user" -> StreamEvent.UserTurn(
            root.string("uuid"),
            ContentBlock.parseContent(root.obj("message")?.get("content")),
            root.string("parent_tool_use_id"),
        )

        "result" -> parseResult(root)
        else -> null
    }

    private fun parseSystem(root: JsonObject): StreamEvent? {
        val taskId = root.string("task_id")
        return when (root.string("subtype")) {
            "init" -> StreamEvent.SessionStarted(
                root.string("session_id").orEmpty(),
                root.string("model"),
                root.array("capabilities")?.mapNotNull { it.asString }.orEmpty(),
                root.array("slash_commands")?.mapNotNull { it.asString }.orEmpty(),
            )

            "task_started" -> taskId?.let {
                StreamEvent.TaskStarted(
                    it,
                    root.string("tool_use_id"),
                    root.string("description").orEmpty(),
                    root.string("subagent_type"),
                    root.bool("is_backgrounded"),
                )
            }

            "task_updated" -> taskId?.let {
                StreamEvent.TaskProgress(it, root.obj("patch")?.string("status"), null)
            }

            "task_notification" -> taskId?.let {
                StreamEvent.TaskProgress(it, root.string("status"), root.string("output_file"))
            }

            "status" -> parseStatus(root)

            "bridge_state" -> root.string("state")?.let { StreamEvent.BridgeState(it) }

            "compact_boundary" -> StreamEvent.Compacted(
                root.obj("compact_metadata")?.string("trigger"),
            )

            "api_retry" -> StreamEvent.ApiRetry(
                root.number("attempt")?.toInt() ?: 0,
                root.number("max_retries")?.toInt() ?: 0,
                root.string("error").orEmpty(),
            )

            else -> null
        }
    }

    private fun parseStatus(root: JsonObject): StreamEvent? {
        val status = root.string("status")
        val result = root.string("compact_result")
        if (status == COMPACTING) return StreamEvent.Compacting(true, null)
        if (result == null) return null
        return StreamEvent.Compacting(false, root.string("compact_error").takeIf { result != "success" })
    }

    private fun parseDelta(root: JsonObject): StreamEvent? {
        val event = root.obj("event") ?: return null
        if (event.string("type") != "content_block_delta") return null
        val delta = event.obj("delta") ?: return null
        return when (delta.string("type")) {
            "text_delta" -> StreamEvent.TextDelta(delta.string("text").orEmpty())
            "thinking_delta" -> StreamEvent.ThinkingDelta(delta.string("thinking").orEmpty())
            else -> null
        }
    }

    private fun parseResult(root: JsonObject): StreamEvent {
        val usage = root.obj("usage")
        return StreamEvent.TurnFinished(
            subtype = root.string("subtype").orEmpty(),
            costUsd = root.number("total_cost_usd"),
            inputTokens = usage?.number("input_tokens")?.toLong(),
            outputTokens = usage?.number("output_tokens")?.toLong(),
            errorText = if (root.bool("is_error")) root.string("result") else null,
        )
    }
}
