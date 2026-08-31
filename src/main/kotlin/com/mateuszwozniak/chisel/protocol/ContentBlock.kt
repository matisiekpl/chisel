package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject

sealed interface ContentBlock {

    data class Text(val text: String) : ContentBlock

    data class Thinking(val text: String) : ContentBlock

    data class ToolUse(val id: String, val name: String, val input: JsonObject) : ContentBlock

    data class ToolResult(val toolUseId: String, val text: String, val isError: Boolean) : ContentBlock

    companion object {

        fun parseList(content: JsonArray?): List<ContentBlock> =
            content.orEmpty().mapNotNull { parse(it as? JsonObject ?: return@mapNotNull null) }

        private fun parse(block: JsonObject): ContentBlock? = when (block.string("type")) {
            "text" -> Text(block.string("text").orEmpty())
            "thinking" -> Thinking(block.string("thinking").orEmpty())
            "tool_use" -> ToolUse(
                block.string("id").orEmpty(),
                block.string("name").orEmpty(),
                block.getAsJsonObject("input") ?: JsonObject(),
            )

            "tool_result" -> ToolResult(
                block.string("tool_use_id").orEmpty(),
                flattenResultContent(block),
                block.bool("is_error"),
            )

            else -> null
        }

        private fun flattenResultContent(block: JsonObject): String {
            val content = block.get("content") ?: return ""
            if (content.isJsonPrimitive) return content.asString
            if (!content.isJsonArray) return content.toString()
            return content.asJsonArray
                .mapNotNull { (it as? JsonObject)?.string("text") }
                .joinToString("\n")
        }

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()
    }
}
