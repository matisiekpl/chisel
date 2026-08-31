package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.QueuedPrompt
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import com.mateuszwozniak.chisel.ui.approval.RewindConfirmationDialog
import com.mateuszwozniak.chisel.util.ProjectPaths
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ConversationPanel(
    private val project: Project,
    private val controller: ConversationController,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val html = TranscriptHtml(MarkdownRenderer(project), project.basePath)

    private var agentsDialog: AgentsDialog? = null
    private var dismissedAgents = false
    private val transcript = TranscriptPanel(project, controller) { messageUuid, restorePrompt ->
        requestRewind(messageUuid, restorePrompt)
    }
    private val agentsLink =
        BadgeButton("View running subagents", AnimatedIcon.Default.INSTANCE) { openAgents() }
    private val agentsBar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
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
        input.commands = { controller.conversation.slashCommands.toList() }
        agentsBar.isOpaque = false
        agentsBar.border = JBUI.Borders.empty(8, 12, 8, 12)
        agentsBar.isVisible = false
        agentsBar.add(agentsLink)

        val header = JPanel(BorderLayout())
        header.add(agentsBar, BorderLayout.NORTH)
        header.add(todoPanel, BorderLayout.CENTER)

        val center = JPanel(BorderLayout())
        center.add(header, BorderLayout.NORTH)
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

    override fun onTasksChanged() = onEventDispatchThread { syncAgents() }

    override fun onBusyChanged(busy: Boolean) = onEventDispatchThread {
        input.showBusy(busy)
    }

    override fun onTitleChanged(title: String) = onEventDispatchThread {
        ConversationTabs.retitle(project, controller)
    }

    val focusTarget: JComponent get() = input.focusTarget

    private fun syncAgents() {
        val running = controller.conversation.tasks.any { !it.finished }
        if (!running) {
            dismissedAgents = false
            agentsBar.isVisible = false
            return
        }
        if (agentsDialog == null && !dismissedAgents) openAgents()
        agentsBar.isVisible = agentsDialog == null
    }

    private fun openAgents() {
        if (agentsDialog != null) return
        val dialog = AgentsDialog(project, controller, html)
        agentsDialog = dialog
        agentsBar.isVisible = false
        Disposer.register(dialog.disposable, Disposable {
            agentsDialog = null
            dismissedAgents = true
            syncAgents()
        })
        dialog.show()
    }

    fun focusInput() {
        input.requestFocusOnField()
    }

    override fun dispose() {
        controller.removeListener(this)
        agentsDialog?.close(DialogWrapper.CANCEL_EXIT_CODE)
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
