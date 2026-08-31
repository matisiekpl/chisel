package com.mateuszwozniak.chisel.protocol

sealed interface StreamEvent {

    data class SessionStarted(
        val sessionId: String,
        val model: String?,
        val capabilities: List<String>,
        val slashCommands: List<String>,
    ) : StreamEvent

    data class TaskStarted(
        val taskId: String,
        val toolUseId: String?,
        val description: String,
        val subagentType: String?,
        val backgrounded: Boolean,
    ) : StreamEvent

    data class TaskProgress(
        val taskId: String,
        val status: String?,
        val outputFile: String?,
    ) : StreamEvent

    data class Compacted(val trigger: String?) : StreamEvent

    data class Compacting(val running: Boolean, val error: String?) : StreamEvent

    data class TextDelta(val text: String) : StreamEvent

    data class ThinkingDelta(val text: String) : StreamEvent

    data class AssistantTurn(
        val blocks: List<ContentBlock>,
        val parentToolUseId: String?,
    ) : StreamEvent

    data class UserTurn(
        val uuid: String?,
        val blocks: List<ContentBlock>,
        val parentToolUseId: String?,
    ) : StreamEvent

    data class ApiRetry(val attempt: Int, val maxRetries: Int, val error: String) : StreamEvent

    data class TurnFinished(
        val subtype: String,
        val costUsd: Double?,
        val inputTokens: Long?,
        val outputTokens: Long?,
        val errorText: String?,
    ) : StreamEvent
}
