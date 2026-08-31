package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.mateuszwozniak.chisel.service.ConversationController

object ConversationTabs {

    const val TOOL_WINDOW_ID = "Chisel"

    val CONTROLLER: Key<ConversationController> = Key.create("chisel.conversation.controller")

    fun open(project: Project, controller: ConversationController) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate { add(project, toolWindow, controller) }
    }

    fun close(project: Project, controller: ConversationController) {
        val contentManager = ToolWindowManager.getInstance(project)
            .getToolWindow(TOOL_WINDOW_ID)
            ?.contentManagerIfCreated ?: return
        contentManager.contents
            .firstOrNull { it.getUserData(CONTROLLER) === controller }
            ?.let { contentManager.removeContent(it, true) }
    }

    fun retitle(project: Project, controller: ConversationController) {
        val contentManager = ToolWindowManager.getInstance(project)
            .getToolWindow(TOOL_WINDOW_ID)
            ?.contentManagerIfCreated ?: return
        contentManager.contents
            .firstOrNull { it.getUserData(CONTROLLER) === controller }
            ?.displayName = controller.conversation.title
    }

    fun add(
        project: Project,
        toolWindow: ToolWindow,
        controller: ConversationController,
    ): Content {
        val existing = toolWindow.contentManager.contents
            .firstOrNull { it.getUserData(CONTROLLER) === controller }
        if (existing != null) {
            toolWindow.contentManager.setSelectedContent(existing)
            focusInput(existing)
            return existing
        }
        val panel = ConversationPanel(project, controller)
        val content = ContentFactory.getInstance()
            .createContent(panel, controller.conversation.title, false)
        content.isCloseable = true
        content.setDisposer(panel)
        content.putUserData(CONTROLLER, controller)
        content.preferredFocusableComponent = panel.focusTarget
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        focusInput(content)
        return content
    }

    fun focusInput(content: Content) {
        val panel = content.component as? ConversationPanel ?: return
        ApplicationManager.getApplication().invokeLater { panel.focusInput() }
    }
}
