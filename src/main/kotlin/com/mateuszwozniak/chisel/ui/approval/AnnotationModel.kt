package com.mateuszwozniak.chisel.ui.approval

import com.mateuszwozniak.chisel.model.LineAnnotation

class AnnotationModel {

    private val entries = sortedMapOf<Int, String>()

    var onChanged: () -> Unit = {}

    fun put(line: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) entries.remove(line) else entries[line] = trimmed
        onChanged()
    }

    fun remove(line: Int) {
        entries.remove(line)
        onChanged()
    }

    fun textAt(line: Int): String? = entries[line]

    fun hasAny(): Boolean = entries.isNotEmpty()

    fun all(): List<LineAnnotation> = entries.map { LineAnnotation(it.key + 1, it.value) }
}
