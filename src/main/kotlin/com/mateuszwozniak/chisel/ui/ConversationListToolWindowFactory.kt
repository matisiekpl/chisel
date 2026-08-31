package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.mateuszwozniak.chisel.service.ConversationManager

class ConversationListToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ConversationManager.getInstance(project).restore()
        val panel = ConversationListPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.isCloseable = false
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
