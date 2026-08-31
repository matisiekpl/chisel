package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
        val view = ConversationView.getInstance(project)
        val conversations = manager.restore()
        val state = ConversationState.getInstance(project).state
        val initial = conversations.firstOrNull { it.conversation.id == state.selectedId }
            ?: conversations.first()
        view.show(toolWindow, initial)
        manager.persist()

        toolWindow.setTitleActions(listOf(NewConversationAction(project)))

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.operation != ContentManagerEvent.ContentOperation.add) return
                event.content.getUserData(ConversationView.CONTROLLER)?.let {
                    state.selectedId = it.conversation.id
                }
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                val controller = event.content.getUserData(ConversationView.CONTROLLER)
                if (controller != null && controller.conversation.transcript.isEmpty()) {
                    view.release(controller)
                    manager.delete(controller)
                } else {
                    manager.persist()
                }
                openFreshWhenEmpty(project, toolWindow, manager, view)
            }
        })
    }

    private fun openFreshWhenEmpty(
        project: Project,
        toolWindow: ToolWindow,
        manager: ConversationManager,
        view: ConversationView,
    ) {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                if (toolWindow.contentManager.contentCount > 0) return@invokeLater
                view.show(toolWindow, manager.create())
            },
            project.disposed,
        )
    }

    private fun showMissingCli(toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(MissingCliPanel(), null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
