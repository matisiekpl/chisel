package com.mateuszwozniak.chisel.service

import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.TranscriptItem

object TranscriptExport {

    fun toMarkdown(conversation: Conversation): String {
        val body = conversation.transcript.mapNotNull(::render).joinToString("\n\n")
        return "# " + conversation.title + "\n\n" + body + "\n"
    }

    private fun render(item: TranscriptItem): String? = when (item) {
        is TranscriptItem.UserPrompt -> "## You\n\n" + item.text

        is TranscriptItem.AssistantText -> item.text.takeIf { it.isNotBlank() }

        is TranscriptItem.ToolCall -> "`" + item.name + "`" +
            (if (item.failed) " — failed" else "")

        is TranscriptItem.Notice -> "> " + item.text

        else -> null
    }
}
