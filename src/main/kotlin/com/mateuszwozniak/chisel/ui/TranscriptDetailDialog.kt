package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class TranscriptDetailDialog(
    project: Project,
    title: String,
    private val content: String,
) : DialogWrapper(project) {

    private val view = HtmlView(disposable)

    init {
        setTitle(title)
        init()
        if (content.isNotBlank()) view.setHtml(content)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        if (content.isBlank()) {
            return JBLabel("This call carries no arguments and returned no output.")
                .apply { border = JBUI.Borders.empty(8, 4) }
        }
        val scroll = ScrollPaneFactory.createScrollPane(view.component, true)
        scroll.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        return scroll
    }

    private companion object {
        const val WIDTH = 720
        const val HEIGHT = 520
    }
}
