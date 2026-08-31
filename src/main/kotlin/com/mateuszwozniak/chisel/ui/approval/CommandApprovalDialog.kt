package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class CommandApprovalDialog(
    project: Project,
    private val toolName: String,
    private val details: String,
    private val reason: String?,
) : DialogWrapper(project, true) {

    enum class Outcome { ALLOW, DENY }

    private val reasonField = JBTextArea(2, 40)

    private val allowAction = object : DialogWrapperAction("Allow") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.ALLOW
            close(OK_EXIT_CODE)
        }
    }

    private val denyAction = object : DialogWrapperAction("Deny") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.DENY
            close(CANCEL_EXIT_CODE)
        }
    }

    var outcome: Outcome = Outcome.DENY
        private set

    init {
        title = "Allow $toolName?"
        init()
    }

    fun denyReason(): String = reasonField.text.trim()

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))

        val commandArea = JBTextArea(details)
        commandArea.isEditable = false
        commandArea.lineWrap = true
        commandArea.wrapStyleWord = true
        panel.add(ScrollPaneFactory.createScrollPane(commandArea, true), BorderLayout.CENTER)

        reason?.let { panel.add(JBLabel(it), BorderLayout.NORTH) }

        val footer = JPanel(BorderLayout(0, JBUI.scale(4)))
        footer.add(JBLabel("Reason for denying, sent back to the agent"), BorderLayout.NORTH)
        reasonField.lineWrap = true
        reasonField.wrapStyleWord = true
        footer.add(ScrollPaneFactory.createScrollPane(reasonField, true), BorderLayout.CENTER)
        panel.add(footer, BorderLayout.SOUTH)
        return panel
    }

    override fun createActions(): Array<Action> = arrayOf(allowAction, denyAction)

    override fun getDimensionServiceKey(): String = "Chisel.CommandApproval"

    private companion object {
        const val WIDTH = 620
        const val HEIGHT = 320
    }
}
