package com.mateuszwozniak.chisel.protocol

sealed interface StreamEvent {

    data class SessionStarted(
        val sessionId: String,
        val model: String?,
        val capabilities: List<String>,
    ) : StreamEvent

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
