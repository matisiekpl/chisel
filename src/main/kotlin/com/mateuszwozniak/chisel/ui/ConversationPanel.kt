package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import com.mateuszwozniak.chisel.ui.approval.RewindConfirmationDialog
import com.mateuszwozniak.chisel.util.ProjectPaths
import java.awt.BorderLayout
import javax.swing.JPanel

class ConversationPanel(
    private val project: Project,
    private val controller: ConversationController,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val transcript = TranscriptPanel(project, controller) { requestRewind(it) }
    private val todoPanel = TodoPanel()
    private val modeToggle = ModeToggle(controller.conversation.mode) { controller.changeMode(it) }
    private val input = PromptInput(
        project,
        modeToggle,
        { controller.sendPrompt(it) },
        { controller.interrupt() },
    )

    init {
        val center = JPanel(BorderLayout())
        center.add(todoPanel, BorderLayout.NORTH)
        center.add(transcript, BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)

        add(input, BorderLayout.SOUTH)

        Disposer.register(this, transcript)
        controller.addListener(this)
        todoPanel.update(controller.conversation.todos.toList())
    }

    override fun onTodosChanged(todos: List<TodoItem>) = onEventDispatchThread {
        todoPanel.update(todos)
    }

    override fun onModeChanged(mode: AgentMode) = onEventDispatchThread {
        modeToggle.show(mode)
    }

    override fun onBusyChanged(busy: Boolean) = onEventDispatchThread {
        input.showBusy(busy)
    }

    override fun onTitleChanged(title: String) = onEventDispatchThread {
        ConversationTabs.retitle(project, controller)
    }

    override fun dispose() {
        controller.removeListener(this)
    }

    private fun requestRewind(messageUuid: String) {
        val files = controller.filesTouchedAfter(messageUuid)
            .map { ProjectPaths.relative(project.basePath, it) }
        val dropped = controller.messagesDroppedAfter(messageUuid)
        if (!RewindConfirmationDialog(project, files, dropped).showAndGet()) return
        controller.rewind(messageUuid) { promptText ->
            onEventDispatchThread {
                promptText?.let { input.text = it }
                input.requestFocusOnField()
            }
        }
    }

    private fun onEventDispatchThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.defaultModalityState())
    }
}
