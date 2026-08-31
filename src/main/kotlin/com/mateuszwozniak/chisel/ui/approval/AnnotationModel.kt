package com.mateuszwozniak.chisel.ui.approval

import com.mateuszwozniak.chisel.model.LineAnnotation

class AnnotationModel {

    private val entries = sortedMapOf<Int, String>()

    var onChanged: () -> Unit = {}

    var onCleared: () -> Unit = {}

    var onCommentRequested: () -> Unit = {}

    var caretLine: Int = -1
        set(value) {
            if (field == value) return
            field = value
            onChanged()
        }

    fun hasCommentAtCaret(): Boolean = textAt(caretLine) != null

    fun requestComment() = onCommentRequested()

    fun put(line: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) entries.remove(line) else entries[line] = trimmed
        onChanged()
    }

    fun clear() {
        entries.clear()
        onCleared()
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
