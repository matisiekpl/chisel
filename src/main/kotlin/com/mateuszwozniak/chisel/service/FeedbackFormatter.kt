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
            "I did not accept the write to $displayPath. The file on disk is unchanged."
        )

        if (annotations.isNotEmpty()) {
            val lines = annotations.sortedBy { it.line }
                .joinToString("\n") { "- line ${it.line}: ${it.text}" }
            sections.add("Comments on the proposed content:\n$lines")
        }

        if (editedContent != null) {
            sections.add("My corrected version of the file:\n```$languageId\n$editedContent\n```")
        }

        sections.add(
            "Rewrite the file so it satisfies the above, then write it again. " +
                "Carry these decisions into every file you touch afterwards."
        )
        return sections.joinToString("\n\n")
    }

    fun formatRejection(): String =
        "I rejected the write to $displayPath and stopped the turn. Wait for my next instruction."
}
