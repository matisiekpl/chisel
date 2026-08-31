package com.mateuszwozniak.chisel.ui

import com.google.gson.JsonObject
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.string
import com.mateuszwozniak.chisel.util.ProjectPaths

class TranscriptHtml(private val renderer: MarkdownRenderer, private val basePath: String?) {

    fun render(item: TranscriptItem): String = when (item) {
        is TranscriptItem.UserPrompt -> userPrompt(item)
        is TranscriptItem.AssistantText -> renderer.render(item.text)
        is TranscriptItem.Thinking, is TranscriptItem.ToolCall -> ""
        is TranscriptItem.Notice -> notice(item)
        is TranscriptItem.TurnSummary -> ""
    }

    fun detail(item: TranscriptItem.ToolCall): String = toolDetail(item)

    fun toolSummary(name: String, input: JsonObject): String = when (name) {
        "Bash" -> input.string("command").orEmpty()
        "Grep" -> input.string("pattern").orEmpty()
        "Glob" -> input.string("pattern").orEmpty()
        "Task" -> input.string("description").orEmpty()
        "WebFetch" -> input.string("url").orEmpty()
        "WebSearch" -> input.string("query").orEmpty()
        else -> filePathOf(input)?.let { relative(it) } ?: ""
    }

    fun filePathOf(input: JsonObject): String? =
        input.string("file_path") ?: input.string("notebook_path")

    fun relative(path: String): String = ProjectPaths.relative(basePath, path)

    private fun userPrompt(item: TranscriptItem.UserPrompt): String =
        "<div>${escape(item.text).replace("\n", "<br>")}</div>"

    private fun toolDetail(item: TranscriptItem.ToolCall): String {
        val arguments = pretty(item.input)
            .takeIf { it.isNotBlank() }
            ?.let { "<pre><code>${escape(wrap(it))}</code></pre>" }
            .orEmpty()
        val result = item.result
            ?.takeIf { it.isNotBlank() }
            ?.let {
                val marker = if (item.failed) "<p><b>Failed</b></p>" else ""
                marker + "<pre><code>${escape(wrap(truncate(it)))}</code></pre>"
            }
            .orEmpty()
        return arguments + result
    }

    private fun notice(item: TranscriptItem.Notice): String {
        val label = if (item.failed) "Error" else "Notice"
        return "<p><b>$label</b> ${escape(item.text)}</p>"
    }

    private fun pretty(input: JsonObject): String = input.entrySet().joinToString("\n") { entry ->
        val value = if (entry.value.isJsonPrimitive) entry.value.asString else entry.value.toString()
        "${entry.key}: ${truncate(value)}"
    }

    fun shorten(text: String): String {
        val line = text.trim().replace('\n', ' ')
        return if (line.length <= SUMMARY_LIMIT) line else line.take(SUMMARY_LIMIT).trimEnd() + "\u2026"
    }

    private fun wrap(text: String): String = text.lineSequence().joinToString("\n") { line ->
        if (line.length <= WRAP_LIMIT) line else line.chunked(WRAP_LIMIT).joinToString("\n")
    }

    private fun truncate(text: String): String =
        if (text.length <= CONTENT_LIMIT) text else text.take(CONTENT_LIMIT) + "\n[truncated]"

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private companion object {
        const val CONTENT_LIMIT = 4000
        const val SUMMARY_LIMIT = 80
        const val WRAP_LIMIT = 100
    }
}
