package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.service.ConversationController
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class AgentsDialog(
    project: Project,
    controller: ConversationController,
    html: TranscriptHtml,
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val view = AgentsView(project, controller, html)

    init {
        title = "Subagents"
        setOKButtonText("Close")
        init()
        Disposer.register(disposable, view)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        view.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        return view
    }

    override fun getDimensionServiceKey(): String = "Chisel.Agents"

    private companion object {
        const val WIDTH = 620
        const val HEIGHT = 360
    }
}
