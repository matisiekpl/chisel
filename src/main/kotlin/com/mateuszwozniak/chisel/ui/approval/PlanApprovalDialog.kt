package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.ui.HtmlView
import com.mateuszwozniak.chisel.ui.MarkdownRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class PlanApprovalDialog(
    private val project: Project,
    private val planMarkdown: String,
) : DialogWrapper(project, true) {

    enum class Outcome { IMPLEMENT, KEEP_PLANNING }

    private val notesField = JBTextArea(3, 40)

    private val implementAction = object : DialogWrapperAction("Start implementing") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.IMPLEMENT
            close(OK_EXIT_CODE)
        }
    }

    private val keepPlanningAction = object : DialogWrapperAction("Keep planning") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.KEEP_PLANNING
            close(CANCEL_EXIT_CODE)
        }
    }

    var outcome: Outcome = Outcome.KEEP_PLANNING
        private set

    init {
        title = "Plan ready"
        init()
    }

    fun notes(): String = notesField.text.trim()

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))

        val view = HtmlView(disposable)
        panel.add(ScrollPaneFactory.createScrollPane(view.component, true), BorderLayout.CENTER)
        renderPlanInto(view)

        val footer = JPanel(BorderLayout(0, JBUI.scale(4)))
        footer.add(JBLabel("Notes, sent back to the agent when you keep planning"), BorderLayout.NORTH)
        notesField.lineWrap = true
        notesField.wrapStyleWord = true
        footer.add(ScrollPaneFactory.createScrollPane(notesField, true), BorderLayout.CENTER)
        panel.add(footer, BorderLayout.SOUTH)
        return panel
    }

    override fun createActions(): Array<Action> = arrayOf(implementAction, keepPlanningAction)

    override fun getDimensionServiceKey(): String = "Chisel.PlanApproval"

    private fun renderPlanInto(view: HtmlView) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val rendered = runCatching { MarkdownRenderer(project).render(planMarkdown) }.getOrElse { "" }
            ApplicationManager.getApplication().invokeLater(
                { view.setHtml(rendered) },
                ModalityState.any(),
            )
        }
    }

    private companion object {
        const val WIDTH = 760
        const val HEIGHT = 520
    }
}
