package com.mateuszwozniak.chisel.service

import com.mateuszwozniak.chisel.model.LineAnnotation

class FeedbackFormatter(private val displayPath: String) {

    fun format(
        annotations: List<LineAnnotation>,
        editedContent: String?,
        languageId: String,
    ): String {
        val sections = mutableListOf<String>()
        sections.add(
            "I reviewed your write to $displayPath in the diff editor and it is not accepted yet. " +
                "Everything below is written by me, not by tooling."
        )

        if (editedContent != null) {
            sections.add("My corrected version of the file:\n```$languageId\n$editedContent\n```")
        }

        if (annotations.isNotEmpty()) {
            val lines = annotations.sortedBy { it.line }
                .joinToString("\n") { "- line ${it.line}: ${it.text}" }
            sections.add("My comments on specific lines:\n$lines")
        }

        sections.add(closing(annotations.isNotEmpty() && editedContent != null))
        return sections.joinToString("\n\n")
    }

    fun formatRejection(): String =
        "I rejected the write to $displayPath and stopped the turn. Wait for my next instruction."

    private fun closing(hasBoth: Boolean): String {
        val precedence = if (hasBoth) {
            "My comments are the newer instruction: wherever a comment contradicts my corrected " +
                "version, follow the comment. "
        } else {
            ""
        }
        return precedence + "Apply all of this without asking me to choose between the two, " +
            "write the file again, and carry these decisions into every file you touch afterwards."
    }
}
