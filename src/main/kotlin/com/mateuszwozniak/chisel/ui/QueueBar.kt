package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.model.QueuedPrompt
import java.awt.FlowLayout
import javax.swing.JPanel

class QueueBar(private val onRemove: (QueuedPrompt) -> Unit) :
    JPanel(VerticalLayout(JBUI.scale(GAP))) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(0, 4, 6, 4)
        isVisible = false
    }

    fun show(queued: List<QueuedPrompt>) {
        removeAll()
        if (queued.isNotEmpty()) {
            add(
                JBLabel("Queued").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                }
            )
        }
        queued.forEach { prompt ->
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
            row.isOpaque = false
            row.add(PromptChip(label(prompt), AllIcons.Actions.Play_forward, prompt.text) { onRemove(prompt) })
            add(row)
        }
        if (queued.isNotEmpty()) {
            add(
                JBLabel("Press \u2191 to edit the last queued message").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                }
            )
        }
        isVisible = queued.isNotEmpty()
        revalidate()
        repaint()
    }

    private fun label(prompt: QueuedPrompt): String {
        val line = prompt.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val shortened = if (line.length <= LIMIT) line else line.take(LIMIT).trimEnd() + "…"
        if (prompt.attachments.isEmpty()) return shortened
        return shortened + "  +" + prompt.attachments.size + " file(s)"
    }

    private companion object {
        const val GAP = 4
        const val LIMIT = 60
    }
}
