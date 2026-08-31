package com.mateuszwozniak.chisel.model

import java.util.concurrent.CopyOnWriteArrayList

class Conversation(
    val id: String,
    var title: String,
    var mode: AgentMode = AgentMode.PLAN,
    var sessionId: String? = null,
) {

    val transcript: MutableList<TranscriptItem> = CopyOnWriteArrayList()

    val todos: MutableList<TodoItem> = CopyOnWriteArrayList()

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
