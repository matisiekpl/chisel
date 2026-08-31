package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.mateuszwozniak.chisel.cli.ClaudeExecutable
import com.mateuszwozniak.chisel.service.ConversationManager
import com.mateuszwozniak.chisel.state.ConversationState

class ChiselToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (ClaudeExecutable.locate() == null) {
            showMissingCli(toolWindow)
            return
        }
        val manager = ConversationManager.getInstance(project)
        val conversations = manager.restore()
        val state = ConversationState.getInstance(project).state
        val initial = conversations.firstOrNull { it.conversation.id == state.selectedId }
            ?: conversations.first()
        ConversationTabs.add(project, toolWindow, initial)
        manager.persist()

        if (toolWindow is ToolWindowEx) {
            toolWindow.setTabActions(NewConversationAction(project))
        }

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.operation != ContentManagerEvent.ContentOperation.add) return
                event.content.getUserData(ConversationTabs.CONTROLLER)?.let {
                    state.selectedId = it.conversation.id
                }
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                val controller = event.content.getUserData(ConversationTabs.CONTROLLER)
                if (controller != null && controller.conversation.transcript.isEmpty()) {
                    manager.delete(controller)
                    return
                }
                manager.persist()
            }
        })
    }

    private fun showMissingCli(toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(MissingCliPanel(), null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
