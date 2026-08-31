package com.mateuszwozniak.chisel.ui.approval

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.ui.JBColor
import java.awt.Color

class UserEditHighlighter(
    private val editor: EditorEx,
    private val parent: Disposable,
) {

    private val original: CharSequence = editor.document.text

    private val highlighters = mutableListOf<RangeHighlighter>()

    fun install() {
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = refresh()
        }, parent)
    }

    private fun refresh() {
        highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        highlighters.clear()
        editedRanges().forEach(::highlight)
    }

    private fun editedRanges(): List<IntRange> {
        val document = editor.document
        if (document.lineCount > MAX_LINES) return emptyList()
        val fragments = runCatching {
            ComparisonManager.getInstance().compareLinesInner(
                original,
                document.charsSequence,
                ComparisonPolicy.DEFAULT,
                DumbProgressIndicator.INSTANCE,
            )
        }.getOrNull() ?: return emptyList()
        return fragments.flatMap { fragment ->
            val inner = fragment.innerFragments
            if (inner == null) {
                listOf(fragment.startOffset2 until fragment.endOffset2)
            } else {
                inner.map { part ->
                    (fragment.startOffset2 + part.startOffset2) until (fragment.startOffset2 + part.endOffset2)
                }
            }
        }.filterNot { it.isEmpty() }
    }

    private fun highlight(range: IntRange) {
        val end = (range.last + 1).coerceAtMost(editor.document.textLength)
        if (range.first >= end) return
        highlighters.add(
            editor.markupModel.addRangeHighlighter(
                range.first,
                end,
                HighlighterLayer.SELECTION - 1,
                attributes(),
                HighlighterTargetArea.EXACT_RANGE,
            )
        )
    }

    private fun attributes(): TextAttributes = TextAttributes().apply {
        backgroundColor = BACKGROUND
        errorStripeColor = STRIPE
    }

    private companion object {
        const val MAX_LINES = 3000
        val BACKGROUND = JBColor(Color(0xF5, 0xE6, 0xC8), Color(0x4A, 0x3E, 0x24))
        val STRIPE = JBColor(Color(0xD9, 0xA3, 0x3C), Color(0xD9, 0xA3, 0x3C))
    }
}
