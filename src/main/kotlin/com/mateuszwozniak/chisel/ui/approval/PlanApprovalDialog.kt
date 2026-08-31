package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.ui.BadgeShape
import com.mateuszwozniak.chisel.ui.HtmlView
import com.mateuszwozniak.chisel.ui.MarkdownRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class PlanApprovalDialog(
    private val project: Project,
    private val planMarkdown: String,
) : DialogWrapper(project, true) {

    enum class Outcome { IMPLEMENT, KEEP_PLANNING }

    private val feedbackField = JBTextArea(FEEDBACK_ROWS, 40)

    private val submitAction = object : DialogWrapperAction(IMPLEMENT_LABEL) {
        override fun doAction(event: ActionEvent) {
            outcome = if (notes().isEmpty()) Outcome.IMPLEMENT else Outcome.KEEP_PLANNING
            close(OK_EXIT_CODE)
        }
    }

    var outcome: Outcome = Outcome.KEEP_PLANNING
        private set

    init {
        title = "Plan ready"
        submitAction.putValue(DEFAULT_ACTION, true)
        init()
    }

    fun notes(): String = feedbackField.text.trim()

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))

        val view = HtmlView(disposable)
        panel.add(ScrollPaneFactory.createScrollPane(view.component, true), BorderLayout.CENTER)
        renderPlanInto(view)

        panel.add(feedbackCard(), BorderLayout.SOUTH)
        return panel
    }

    private fun feedbackCard(): JComponent {
        feedbackField.lineWrap = true
        feedbackField.wrapStyleWord = true
        feedbackField.isOpaque = false
        feedbackField.font = JBUI.Fonts.label()
        feedbackField.border = JBUI.Borders.empty(2)
        feedbackField.emptyText.text = "Feedback on the plan, sent back to the agent"
        feedbackField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                submitAction.putValue(
                    Action.NAME,
                    if (notes().isEmpty()) IMPLEMENT_LABEL else FEEDBACK_LABEL,
                )
            }
        })

        val scroll = ScrollPaneFactory.createScrollPane(feedbackField, true)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false

        val card = object : JPanel(BorderLayout()) {
            override fun paintComponent(graphics: Graphics) {
                BadgeShape.paint(graphics, width, height, false)
            }
        }
        card.isOpaque = false
        card.border = JBUI.Borders.empty(8, 10)
        card.add(scroll, BorderLayout.CENTER)
        return card
    }

    override fun createActions(): Array<Action> = arrayOf(submitAction)

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
        const val IMPLEMENT_LABEL = "Implement"
        const val FEEDBACK_LABEL = "Send feedback"
        const val FEEDBACK_ROWS = 4
        const val WIDTH = 760
        const val HEIGHT = 520
    }
}
