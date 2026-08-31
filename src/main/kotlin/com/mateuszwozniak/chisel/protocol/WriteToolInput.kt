package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

sealed interface WriteToolInput {

    val filePath: String

    fun currentSide(fileText: String): String

    fun proposedSide(fileText: String): String

    data class Create(override val filePath: String, val content: String) : WriteToolInput {
        override fun currentSide(fileText: String): String = fileText
        override fun proposedSide(fileText: String): String = content
    }

    data class Replace(
        override val filePath: String,
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean,
    ) : WriteToolInput {
        override fun currentSide(fileText: String): String = fileText

        override fun proposedSide(fileText: String): String =
            if (replaceAll) fileText.replace(oldString, newString)
            else fileText.replaceFirst(oldString, newString)
    }

    data class NotebookCell(
        override val filePath: String,
        val cellId: String?,
        val source: String,
    ) : WriteToolInput {
        override fun currentSide(fileText: String): String = ""
        override fun proposedSide(fileText: String): String = source
    }

    companion object {

        val TOOL_NAMES = setOf("Write", "Edit", "NotebookEdit")

        fun parse(toolName: String, input: JsonObject): WriteToolInput? {
            val filePath = input.string("file_path") ?: input.string("notebook_path") ?: return null
            return when (toolName) {
                "Write" -> Create(filePath, input.string("content").orEmpty())

                "Edit" -> Replace(
                    filePath,
                    input.string("old_string").orEmpty(),
                    input.string("new_string").orEmpty(),
                    input.bool("replace_all"),
                )

                "NotebookEdit" -> NotebookCell(
                    filePath,
                    input.string("cell_id"),
                    input.string("new_source").orEmpty(),
                )

                else -> null
            }
        }
    }
}
