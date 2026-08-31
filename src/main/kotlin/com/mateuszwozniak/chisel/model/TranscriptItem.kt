package com.mateuszwozniak.chisel.model

import com.google.gson.JsonObject

sealed interface TranscriptItem {

    val id: String

    class UserPrompt(
        override val id: String,
        val text: String,
        var messageUuid: String? = null,
        val attachments: List<String> = emptyList(),
    ) : TranscriptItem

    class AssistantText(
        override val id: String,
        var text: String = "",
        val parentToolUseId: String? = null,
    ) : TranscriptItem

    class Thinking(override val id: String, var text: String = "") : TranscriptItem

    class ToolCall(
        override val id: String,
        val toolUseId: String,
        val name: String,
        val input: JsonObject,
        val parentToolUseId: String?,
    ) : TranscriptItem {
        var result: String? = null
        var failed: Boolean = false
    }

    class Notice(
        override val id: String,
        val text: String,
        val failed: Boolean = false,
    ) : TranscriptItem

    class TurnSummary(
        override val id: String,
        val costUsd: Double?,
        val inputTokens: Long?,
        val outputTokens: Long?,
    ) : TranscriptItem
}
