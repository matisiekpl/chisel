package com.mateuszwozniak.chisel.model

import java.util.concurrent.CopyOnWriteArrayList

class Conversation(
    val id: String,
    var title: String,
    var mode: AgentMode = AgentMode.PLAN,
    var model: AgentModel = AgentModel.OPUS,
    var effort: EffortLevel = EffortLevel.HIGH,
    var sessionId: String? = null,
) {

    fun options(): SessionOptions = SessionOptions(mode, model, effort)

    val transcript: MutableList<TranscriptItem> = CopyOnWriteArrayList()

    val todos: MutableList<TodoItem> = CopyOnWriteArrayList()

    val slashCommands: MutableList<String> = CopyOnWriteArrayList()

    val tasks: MutableList<AgentTask> = CopyOnWriteArrayList()

    var titleLocked: Boolean = false

    var updatedAt: Long = System.currentTimeMillis()

    var context: ContextUsage? = null

    var costUsd: Double = 0.0

    var inputTokens: Long = 0

    var outputTokens: Long = 0

    fun turn(): Int = transcript.count { it is TranscriptItem.UserPrompt }

    fun truncateAt(messageUuid: String) {
        val index = transcript.indexOfFirst {
            it is TranscriptItem.UserPrompt && it.messageUuid == messageUuid
        }
        if (index < 0) return
        while (transcript.size > index) transcript.removeAt(transcript.size - 1)
    }

    fun promptTextAt(messageUuid: String): String? = transcript
        .filterIsInstance<TranscriptItem.UserPrompt>()
        .firstOrNull { it.messageUuid == messageUuid }
        ?.text
}
