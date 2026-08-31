package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.ui.FeedbackArea
import com.mateuszwozniak.chisel.ui.Shortcuts
import com.mateuszwozniak.chisel.ui.HtmlView
import com.mateuszwozniak.chisel.ui.MarkdownRenderer
import com.mateuszwozniak.chisel.ui.ResizeHandle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class PlanApprovalDialog(
    private val project: Project,
    private val planMarkdown: String,
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    enum class Outcome { IMPLEMENT, KEEP_PLANNING }

    private val feedback = FeedbackArea("Feedback on the plan", FEEDBACK_ROWS)

    private val submitAction =
        object : DialogWrapperAction(Shortcuts.labelled(IMPLEMENT_LABEL, Shortcuts.submitLabel())) {
            override fun doAction(event: ActionEvent) = submit()
        }

    var outcome: Outcome = Outcome.KEEP_PLANNING
        private set

    init {
        title = "Plan ready"
        submitAction.putValue(DEFAULT_ACTION, true)
        feedback.onChanged {
            submitAction.putValue(
                Action.NAME,
                Shortcuts.labelled(
                    if (notes().isEmpty()) IMPLEMENT_LABEL else FEEDBACK_LABEL,
                    Shortcuts.submitLabel(),
                ),
            )
        }
        init()
        Shortcuts.install(rootPane, Shortcuts.submit()) { submit() }
    }

    fun notes(): String = feedback.text.trim()

    private fun submit() {
        outcome = if (notes().isEmpty()) Outcome.IMPLEMENT else Outcome.KEEP_PLANNING
        close(OK_EXIT_CODE)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))

        val view = HtmlView(disposable)
        val scroll = ScrollPaneFactory.createScrollPane(view.component, true)
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        panel.add(scroll, BorderLayout.CENTER)
        renderPlanInto(view)

        val footer = JPanel(BorderLayout())
        footer.isOpaque = false
        footer.add(ResizeHandle { feedback.resizeBy(it) }, BorderLayout.NORTH)
        footer.add(feedback, BorderLayout.CENTER)
        panel.add(footer, BorderLayout.SOUTH)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = feedback.focusTarget

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
