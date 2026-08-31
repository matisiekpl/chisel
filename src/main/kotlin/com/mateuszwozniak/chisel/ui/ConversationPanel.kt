package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.model.QueuedPrompt
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import com.mateuszwozniak.chisel.ui.approval.RewindConfirmationDialog
import com.mateuszwozniak.chisel.util.ProjectPaths
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ConversationPanel(
    private val project: Project,
    private val controller: ConversationController,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val transcript = TranscriptPanel(project, controller) { messageUuid, restorePrompt ->
        requestRewind(messageUuid, restorePrompt)
    }
    private val todoPanel = TodoPanel()
    private val options = OptionsBar(controller)
    private val input = PromptInput(
        project,
        options,
        { text, attachments -> controller.sendPrompt(text, attachments) },
        { controller.interrupt() },
    )

    init {
        input.onQueueRemove = { prompt -> controller.dropQueued(prompt) }
        input.onQueuePop = { controller.popLastQueued() }
        input.onHistory = { index -> controller.promptAt(index) }
        val center = JPanel(BorderLayout())
        center.add(todoPanel, BorderLayout.NORTH)
        center.add(transcript, BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)

        add(input, BorderLayout.SOUTH)

        Disposer.register(this, transcript)
        controller.addListener(this)
        todoPanel.update(controller.conversation.todos.toList())
        input.showQueue(controller.queued())
    }

    override fun onTodosChanged(todos: List<TodoItem>) = onEventDispatchThread {
        todoPanel.update(todos)
    }

    override fun onOptionsChanged() = onEventDispatchThread {
        options.refresh()
    }

    override fun onQueueChanged(queued: List<QueuedPrompt>) = onEventDispatchThread {
        input.showQueue(queued)
    }

    override fun onBusyChanged(busy: Boolean) = onEventDispatchThread {
        input.showBusy(busy)
    }

    override fun onTitleChanged(title: String) = onEventDispatchThread {
        ConversationTabs.retitle(project, controller)
    }

    val focusTarget: JComponent get() = input.focusTarget

    fun focusInput() {
        input.requestFocusOnField()
    }

    override fun dispose() {
        controller.removeListener(this)
    }

    private fun requestRewind(messageUuid: String, restorePrompt: Boolean) {
        val files = controller.filesTouchedAfter(messageUuid)
            .map { ProjectPaths.relative(project.basePath, it) }
        val dropped = controller.messagesDroppedAfter(messageUuid)
        if (!RewindConfirmationDialog(project, files, dropped, restorePrompt).showAndGet()) return
        controller.rewind(messageUuid) { promptText ->
            onEventDispatchThread {
                if (restorePrompt) promptText?.let { input.text = it }
                input.requestFocusOnField()
            }
        }
    }

    private fun onEventDispatchThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.defaultModalityState())
    }
}
