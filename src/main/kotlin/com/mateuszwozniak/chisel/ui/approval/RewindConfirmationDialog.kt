package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class RewindConfirmationDialog(
    project: Project,
    private val files: List<String>,
    private val droppedMessages: Int,
    private val editing: Boolean,
) : DialogWrapper(project, true) {

    init {
        title = if (editing) "Edit message" else "Rewind conversation"
        setOKButtonText(if (editing) "Edit" else "Rewind")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))

        val opening = if (editing) "The message returns to the input for editing. " else ""
        val headline = opening + "This restores files written by Write, Edit and NotebookEdit, " +
            "and drops $droppedMessages message(s) from the conversation. Changes made through " +
            "Bash and edits applied by subagents are not restored."
        panel.add(JBLabel("<html>$headline</html>"), BorderLayout.NORTH)

        val label = if (files.isEmpty()) "No tracked file changes to restore"
        else "Files to restore"
        val list = JBList(files)
        val body = JPanel(BorderLayout(0, JBUI.scale(4)))
        body.add(JBLabel(label), BorderLayout.NORTH)
        body.add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER)
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    override fun getDimensionServiceKey(): String = "Chisel.RewindConfirmation"

    private companion object {
        const val WIDTH = 520
        const val HEIGHT = 300
    }
}
