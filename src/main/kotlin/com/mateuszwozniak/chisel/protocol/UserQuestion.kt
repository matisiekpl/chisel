package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

data class UserQuestion(
    val question: String,
    val header: String,
    val multiSelect: Boolean,
    val options: List<QuestionOption>,
) {

    data class QuestionOption(val label: String, val description: String?)

    companion object {

        const val TOOL_NAME = "AskUserQuestion"

        fun parse(toolName: String, input: JsonObject): List<UserQuestion>? {
            if (toolName != TOOL_NAME) return null
            val questions = input.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            val parsed = questions.mapNotNull { it as? JsonObject }.mapNotNull(::question)
            return parsed.takeIf { it.isNotEmpty() }
        }

        private fun question(source: JsonObject): UserQuestion? {
            val text = source.string("question") ?: return null
            val options = source.get("options")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
            return UserQuestion(
                text,
                source.string("header").orEmpty(),
                source.bool("multiSelect"),
                options.mapNotNull { it as? JsonObject }.mapNotNull(::option),
            )
        }

        private fun option(source: JsonObject): QuestionOption? {
            val label = source.string("label") ?: return null
            return QuestionOption(label, source.string("description"))
        }
    }
}
